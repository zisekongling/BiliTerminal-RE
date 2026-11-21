#!/usr/bin/env node
// scripts/devtest/adb.mjs —— 设备测试薄封装：动作 + 取数，不做业务判定（26.11.19）
//
// 为什么有它（实测见 docs/review/agent-test-guide.md 4.10）：
//   经工具层跑一条 adb 约 1.6 s（进程启动占大头），程序里直连 0.1 s；
//   uiautomator dump 2.5 s 而截图 0.35 s。成本 = 步数 x (往返 + 看屏幕)，
//   所以固定脚本的价值是：一条命令跑完一步、输出可断言、那份控件树解析不用重写。
//
// 模块用法（run_code 或任何 ESM）：
//   const { makeDriver } = await import("file:///D:/.../scripts/devtest/adb.mjs");
//   const d = makeDriver();                        // 自动挑唯一的在线设备
//   d.amstart(".activity.settings.SettingGroupActivity", ["--es", "group_type", "dev"]);
//   d.tap(d.find({ rid: "setting_switch" }));
//   console.log(d.bridge("get", ["--es", "key", "dev_logv"]));
//   console.error(d.report());                     // 每步耗时与总计
//
// 命令行用法：
//   node scripts/devtest/adb.mjs devices
//   node scripts/devtest/adb.mjs top
//   node scripts/devtest/adb.mjs start .activity.settings.SettingGroupActivity --es group_type dev
//   node scripts/devtest/adb.mjs open SettingGroupActivity "group_type=dev"
//   node scripts/devtest/adb.mjs dump --json --filter 设置
//   node scripts/devtest/adb.mjs find --rid setting_switch
//   node scripts/devtest/adb.mjs tap --rid setting_switch
//   node scripts/devtest/adb.mjs tap 120,200
//   node scripts/devtest/adb.mjs get dev_logv
//   node scripts/devtest/adb.mjs wait --text 首页 --timeout 5000
//   node scripts/devtest/adb.mjs shot .dsh/shot.png
//   node scripts/devtest/adb.mjs --coexist top        // 共存版（包名加 .coexist）
//
// 约定：失败即非零退出，绝不静默；--json 输出机器可读结构；-v 打印每步耗时。
// 边界：不做业务断言（「这页该有三项」留给调用方）；不猜控件——找不到就报错，
// 不会自动点「看起来最像」的那个（真机上每一次点击都有后果）。

import { execFileSync } from "node:child_process";
import fs from "node:fs";
import { pathToFileURL } from "node:url";

export const DEFAULT_PKG = "com.RobinNotBad.BiliClient";
// 调试桥类名两个 flavor 相同（实测：共存版组件是 com.RobinNotBad.BiliClient.coexist/com.RobinNotBad.BiliClient.debug.DebugBridge，
// 写成 com.RobinNotBad.BiliClient.coexist/.debug.DebugBridge 反而投不到）。见 app/src/debug/AndroidManifest.xml。
export const BRIDGE = "com.RobinNotBad.BiliClient.debug.DebugBridge";
export const TOKEN = "rebili-agent-bridge";
export const ADB = process.env.ADB_PATH || "D:\\Program Files\\android-sdk\\platform-tools\\adb.exe";

/** adb 设备表；每项 { serial, state } */
export function listDevices() {
  const out = execFileSync(ADB, ["devices"], { encoding: "utf8" });
  return out
    .split("\n")
    .slice(1)
    .map((l) => l.trim())
    .filter(Boolean)
    .map((l) => {
      const parts = l.split(/\s+/);
      return { serial: parts[0], state: parts[1] };
    });
}

/** --serial > ANDROID_SERIAL > 唯一的在线设备；多台就问清楚，绝不猜 */
export function pickSerial(explicit) {
  if (explicit) return explicit;
  if (process.env.ANDROID_SERIAL) return process.env.ANDROID_SERIAL;
  const online = listDevices().filter((d) => d.state === "device");
  if (online.length === 1) return online[0].serial;
  if (!online.length) throw new Error("没有在线设备：adb devices 为空，先起模拟器或插上真机");
  throw new Error("有多台在线设备，用 --serial 指定其中之一：" + online.map((d) => d.serial).join(", "));
}

function attrOf(s, k) {
  const m = new RegExp(" " + k + '="([^"]*)"').exec(s);
  return m ? m[1] : "";
}

/** 把 uiautomator 的 XML 解析成节点数组（dump 与 find 共用同一份实现） */
export function parseNodes(xml) {
  const list = [];
  for (const m of xml.matchAll(/<node[^>]*>/g)) {
    const s = m[0];
    const b = /bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/.exec(s);
    if (!b) continue;
    const x1 = Number(b[1]);
    const y1 = Number(b[2]);
    const x2 = Number(b[3]);
    const y2 = Number(b[4]);
    list.push({
      cls: attrOf(s, "class").split(".").pop(),
      rid: attrOf(s, "resource-id").split("/").pop(),
      text: attrOf(s, "text"),
      desc: attrOf(s, "content-desc"),
      clickable: attrOf(s, "clickable") === "true",
      enabled: attrOf(s, "enabled") !== "false",
      x: (x1 + x2) >> 1,
      y: (y1 + y2) >> 1,
      bounds: [x1, y1, x2, y2],
    });
  }
  return list;
}

/** 选择器 {rid / text / desc / cls}，可组合（AND）；text 同时匹配 text 与 content-desc */
export function filterNodes(nodes, sel) {
  if (!sel || !(sel.rid || sel.text || sel.desc || sel.cls)) {
    throw new Error("选择器为空：至少给 rid / text / desc / cls 之一");
  }
  return nodes.filter((n) => {
    if (sel.rid && n.rid !== sel.rid) return false;
    if (sel.cls && n.cls !== sel.cls) return false;
    if (sel.text && !((n.text || "").includes(sel.text) || (n.desc || "").includes(sel.text))) return false;
    if (sel.desc && !(n.desc || "").includes(sel.desc)) return false;
    return true;
  });
}

/** 多个命中时挑最该点的那个：可点优先，其次面积小（内层控件） */
export function pickNode(cands) {
  const area = (n) => (n.bounds[2] - n.bounds[0]) * (n.bounds[3] - n.bounds[1]);
  return cands.slice().sort((a, b) => {
    if (a.clickable !== b.clickable) return a.clickable ? -1 : 1;
    return area(a) - area(b);
  })[0];
}

/**
 * 造一个驱动。方法都是同步的（waitFor 除外），每步记耗时。
 * @param {{serial?: string, pkg?: string}} opts
 */
export function makeDriver(opts) {
  const o = opts || {};
  const serial = pickSerial(o.serial);
  const pkg = o.pkg || DEFAULT_PKG;
  const component = pkg + "/" + BRIDGE;
  const steps = [];

  function adbRun(args, runOpts) {
    try {
      return execFileSync(ADB, ["-s", serial].concat(args), Object.assign({
        encoding: "utf8",
        maxBuffer: 64 * 1024 * 1024,
      }, runOpts || {}));
    } catch (e) {
      const code = e.status == null ? "?" : e.status;
      throw new Error("adb 失败（退出码 " + code + "）：" + args.join(" ") + "\n" + String(e.stderr || e.message).trim());
    }
  }

  function step(name, fn) {
    const t0 = Date.now();
    try {
      return fn();
    } finally {
      steps.push({ name: name, ms: Date.now() - t0 });
    }
  }

  const sh = function () {
    return adbRun(["shell"].concat(Array.prototype.slice.call(arguments)));
  };

  /** 调试桥原始调用（--es 对）：130–190 ms 一条 */
  function rawBridge() {
    const extras = Array.prototype.slice.call(arguments);
    const out = adbRun(["shell", "am", "broadcast", "-n", component, "--es", "token", TOKEN].concat(extras));
    const m = /data="([\s\S]*?)"\s*$/.exec(out.trim());
    if (m) return m[1].replace(/\\n/g, "\n");
    // 个别 ROM 不回填 result data：退回读桥自己落的文件（见指南 2.3）
    return adbRun(["shell", "cat", "/storage/emulated/0/Android/data/" + pkg + "/files/logs/bridge-last.txt"]);
  }

  /**
   * 原样取回 data 载荷（不解转义）。桥返回 JSON 时必须用这个：
   * rawBridge 会把 \n 还原成真换行，JSON 里一出现就成非法控制字符。
   */
  function rawBridgeVerbatim() {
    const extras = Array.prototype.slice.call(arguments);
    const out = adbRun(["shell", "am", "broadcast", "-n", component, "--es", "token", TOKEN].concat(extras));
    const m = /data="([\s\S]*?)"\s*$/.exec(out.trim());
    return m ? m[1] : null;
  }

  /** 桥命令：bridge("get", ["--es", "key", "dev_logv"]) */
  function bridge(cmd, args) {
    return rawBridge.apply(null, ["--es", "cmd", cmd].concat(args || []));
  }

  /**
   * 进程内控件树（调试桥 view 命令，130–190 ms）。取不到返回 null。
   * 只有真正的 View 能读到：Compose / WebView 这类自绘界面得走 uiautomator。
   */
  function viewDump(vopts) {
    const v = vopts || {};
    const args = ["--es", "max", String(v.max || 600)];
    if (v.all) args.push("--ez", "all", true);
    // 桥把 JSON 塞进广播的 data 里，引号是 \" 形式、换行是 \n 形式：
    // 先按 JSON 字符串解一层，再按 JSON 解第二层，文字里的换行与引号都不会坏。
    let raw = rawBridgeVerbatim("--es", "cmd", "view", ...args);
    if (raw === null) raw = bridge("view", args);
    if (!raw || raw.indexOf("ERR") === 0) return null;
    const start = raw.indexOf("{");
    if (start < 0) return null;
    const body = raw.slice(start).trim();
    let obj;
    try {
      obj = JSON.parse(JSON.parse('"' + body + '"'));
    } catch (e) {
      try {
        obj = JSON.parse(body.replace(/\\"/g, '"').replace(/[\u0000-\u001f]/g, " "));
      } catch (e2) {
        return null;
      }
    }
    const nodes = (obj.nodes || []).map(function (n) {
      return {
        cls: n.cls || "",
        rid: n.rid || "",
        text: n.text || "",
        desc: n.desc || "",
        clickable: n.clickable === true,
        enabled: n.enabled !== false,
        scrollable: n.scroll === true,
        off: n.off === true,
        x: n.x,
        y: n.y,
        bounds: n.bounds || [n.x, n.y, n.x, n.y],
      };
    });
    // 附带来源信息：哪个页面、是否真有焦点（退到后台时这棵旧窗口仍可读）
    nodes.activity = obj.activity || "";
    nodes.fg = obj.fg === true;
    return nodes;
  }

  /**
   * 整棵控件树 → 数组。
   * mode: "auto"（默认，先进程内后 uiautomator）| "fast"（只进程内）| "uiauto"（只 uiautomator）
   */
  function dump(dumpOpts) {
    const dopt = dumpOpts || {};
    const mode = dopt.mode || "auto";
    if (mode !== "uiauto") {
      const fast = step("view", function () { return viewDump(dopt); });
      if (fast && fast.length) return fast;
      if (mode === "fast") throw new Error("view 取不到控件树：可能不是前台页面，或界面是 Compose/WebView（改用 uiautomator）");
    }
    return step("dump", function () {
      const xml = adbRun(["exec-out", "uiautomator dump /sdcard/dsh.xml >/dev/null 2>&1; cat /sdcard/dsh.xml"]);
      if (dopt.save) fs.writeFileSync(dopt.save, xml);
      return parseNodes(xml);
    });
  }

  /** 找控件：auto 模式下进程内没命中会再用 uiautomator 复核一次 */
  function find(sel, fopts) {
    const mode = (fopts && fopts.mode) || "auto";
    const hit = filterNodes(dump(fopts), sel);
    if (hit.length) return pickNode(hit);
    if (mode === "auto") {
      const slow = filterNodes(dump({ mode: "uiauto" }), sel);
      if (slow.length) return pickNode(slow);
    }
    return null;
  }

  function tapAt(x, y) {
    return step("tap " + x + "," + y, function () {
      sh("input", "tap", String(x), String(y));
      return { x: x, y: y };
    });
  }

  /** tap(节点) / tap({x,y}) / tap("120,200") */
  function tap(target) {
    if (typeof target === "string") {
      const q = target.split(",");
      if (q.length !== 2) throw new Error('tap 的字符串形式是 "x,y"：' + target);
      return tapAt(Number(q[0]), Number(q[1]));
    }
    if (!target || target.x == null) throw new Error("tap 需要节点、{x,y} 或 \"x,y\"");
    // 列表里滚出屏幕的那部分：中心点不在屏幕上，点下去是静默落空
    if (target.off === true) {
      throw new Error("控件中心 [" + target.x + "," + target.y + "] 在屏幕外（列表里滚出去的那部分），先滚动再点");
    }
    return tapAt(target.x, target.y);
  }

  return {
    serial: serial,
    pkg: pkg,
    component: component,
    steps: steps,
    adb: adbRun,
    sh: sh,
    rawBridge: rawBridge,
    bridge: bridge,
    dump: dump,
    /** 只要进程内控件树（不兜底）；取不到返回 null */
    view: viewDump,
    findAll: function (sel, fopts) { return filterNodes(dump(fopts), sel); },
    find: find,
    tap: tap,
    /** 按文字/描述点；找不到就抛（不猜） */
    tapText: function (text, fopts) {
      const hit = filterNodes(dump(fopts), { text: text });
      if (!hit.length) throw new Error("找不到文字/描述含「" + text + "」的控件");
      return tap(pickNode(hit));
    },
    type: function (text) {
      return step("type", function () {
        sh("input", "text", String(text).replace(/ /g, "%s"));
        return text;
      });
    },
    key: function (code) {
      return step("key " + code, function () { sh("input", "keyevent", String(code)); return code; });
    },
    swipe: function (x1, y1, x2, y2, ms) {
      return step("swipe", function () {
        sh("input", "swipe", String(x1), String(y1), String(x2), String(y2), String(ms || 200));
        return { from: [x1, y1], to: [x2, y2], ms: ms || 200 };
      });
    },
    /** 直达页面：am start -W 会等页面起来（热启约 150 ms，冷启 2 s+） */
    amstart: function (activity, extras) {
      return step("amstart " + activity.split(".").pop(), function () {
        const target = activity.indexOf("/") >= 0 ? activity : pkg + "/" + activity;
        const out = adbRun(["shell", "am", "start", "-W", "-n", target].concat(extras || []));
        const g = function (k) { const m = new RegExp(k + ":\\s*(\\S+)").exec(out); return m ? m[1] : ""; };
        return { raw: out.trim(), status: g("Status"), totalTime: g("TotalTime"), waitTime: g("WaitTime") };
      });
    },
    /** 进程内拉起（含 exported=false 的页面），传简单类名；extrasStr 形如 "aid=2;seekReply=-1" */
    open: function (className, extrasStr) {
      const short = className.split(".").pop();
      return step("open " + short, function () {
        return bridge("start", ["--es", "activity", short].concat(extrasStr ? ["--es", "extras", extrasStr] : []));
      });
    },
    top: function () { return step("top", function () { return bridge("top").trim(); }); },
    get: function (key) { return step("get " + key, function () { return bridge("get", ["--es", "key", key]).trim(); }); },
    set: function (key, value, type) {
      return step("set " + key, function () {
        return bridge("set", ["--es", "key", key, "--es", "value", String(value)].concat(type ? ["--es", "type", type] : []));
      });
    },
    /** 读应用自己的日志文件；file 省略就是主日志，bytes 默认 65536（上限 200000） */
    log: function (logOpts) {
      const lo = logOpts || {};
      return step("log", function () {
        return bridge("log", (lo.file ? ["--es", "file", lo.file] : []).concat(lo.bytes ? ["--es", "bytes", String(lo.bytes)] : []));
      });
    },
    trail: function () { return step("trail", function () { return bridge("trail"); }); },
    /** 截图：0.35–0.5 s，比 dump 快 6 倍；判布局/判文字截断优先用它 */
    shot: function (file) {
      return step("shot", function () {
        const buf = execFileSync(ADB, ["-s", serial, "exec-out", "screencap", "-p"], { maxBuffer: 64 * 1024 * 1024 });
        fs.writeFileSync(file, buf);
        return { file: file, bytes: buf.length };
      });
    },
    /** logcat 快照（-d 不阻塞）；tag 可省略，lines 取最后 N 行 */
    logcat: function (tag, lines) {
      return step("logcat", function () {
        const args = ["logcat", "-d", "-v", "time"];
        if (tag) args.push("-s", tag);
        if (lines) args.push("-t", String(lines));
        return sh.apply(null, args);
      });
    },
    sleep: function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); },
    /** 轮询到选择器命中；超时抛错（不静默继续），返回命中的节点 */
    waitFor: async function (sel, waitOpts) {
      const wo = waitOpts || {};
      const timeout = wo.timeout || 6000;
      const interval = wo.interval || 250;
      const t0 = Date.now();
      while (Date.now() - t0 < timeout) {
        const hit = filterNodes(dump(wo), sel);
        if (hit.length) return pickNode(hit);
        await new Promise(function (r) { setTimeout(r, interval); });
      }
      throw new Error("等待超时（" + timeout + " ms）：" + JSON.stringify(sel));
    },
    /** 每步耗时账单 */
    report: function () {
      const total = steps.reduce(function (a, s) { return a + s.ms; }, 0);
      const lines = steps.map(function (s) { return String(s.ms).padStart(6) + "ms  " + s.name; });
      lines.push(String(total).padStart(6) + "ms  总计 " + steps.length + " 步");
      return lines.join("\n");
    },
  };
}

// ---------------- 命令行 ----------------

const HELP = [
  "用法: node scripts/devtest/adb.mjs <命令> [参数]",
  "",
  "取数  devices",
  "      top                                    当前页面（桥 top）",
  "      dump [--json] [--filter 文本] [--save 文件]",
  "      find --rid X | --text X | --desc X | --cls X   找不到退出码 1",
  "      log [--file 文件名] [--bytes N] | logcat [--tag X] [--lines N] | trail",
  "动作  start <Activity> [--es key value ...]    am start -W 直达",
  "      open <类名> [extras 串]                  进程内拉起（可非 exported），串形如 aid=2;seekReply=-1",
  "      tap --rid X | tap --text X | tap 120,200",
  "      type <文本> | key <KEYCODE> | swipe x1 y1 x2 y2 [ms] | shot <文件>",
  "      wait --rid X [--timeout ms]",
  "偏好  get <key> | set <key> <value> [--type int|bool|string]",
  "",
  "通用: --serial <序列号>  --coexist  --json  -v",
].join("\n");

function parseArgv(argv) {
  const f = { extras: [], pos: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const next = function () { i += 1; return argv[i]; };
    if (a === "--es") { f.extras.push("--es", next(), next()); }
    else if (a === "--serial") f.serial = next();
    else if (a === "--pkg") f.pkg = next();
    else if (a === "--coexist") f.coexist = true;
    else if (a === "--json") f.json = true;
    else if (a === "-v" || a === "--verbose") f.verbose = true;
    else if (a === "--rid") f.rid = next();
    else if (a === "--text") f.text = next();
    else if (a === "--desc") f.desc = next();
    else if (a === "--cls") f.cls = next();
    else if (a === "--filter") f.filter = next();
    else if (a === "--save") f.save = next();
    else if (a === "--timeout") f.timeout = Number(next());
    else if (a === "--file") f.file = next();
    else if (a === "--bytes") f.bytes = Number(next());
    else if (a === "--tag") f.tag = next();
    else if (a === "--lines") f.lines = Number(next());
    else if (a === "--type") f.type = next();
    else if (a === "--fast") f.mode = "fast";
    else if (a === "--uiauto") f.mode = "uiauto";
    else if (a === "--all") f.all = true;
    else if (a === "-h" || a === "--help") f.help = true;
    else f.pos.push(a);
  }
  return f;
}

function selectorOf(f) {
  const sel = {};
  if (f.rid) sel.rid = f.rid;
  if (f.text) sel.text = f.text;
  if (f.desc) sel.desc = f.desc;
  if (f.cls) sel.cls = f.cls;
  return sel;
}

function formatNode(n) {
  const label = n.text || n.desc || "";
  return (n.rid || "-") + " " + n.cls + " 「" + label + "」 @" + n.x + "," + n.y +
    (n.clickable ? " *可点" : "") + (n.off ? " *屏幕外" : "");
}

async function main(argv) {
  const f = parseArgv(argv);
  const cmd = f.pos[0];
  if (!cmd || f.help || cmd === "help") { console.log(HELP); return 0; }

  if (cmd === "devices") {
    const list = listDevices();
    if (f.json) { console.log(JSON.stringify(list)); return 0; }
    list.forEach(function (d) { console.log(d.serial + "\t" + d.state); });
    return 0;
  }

  const pkg = f.pkg || (f.coexist ? DEFAULT_PKG + ".coexist" : DEFAULT_PKG);
  const d = makeDriver({ serial: f.serial, pkg: pkg });
  const sel = selectorOf(f);

  try {
    if (cmd === "top") {
      const top = d.top();
      console.log(f.json ? JSON.stringify({ top: top }) : top);
      return 0;
    }
    if (cmd === "view") {
      const nodes = d.view({ all: f.all });
      if (!nodes) { console.error("进程内控件树取不到（不是前台页面，或界面是 Compose/WebView）"); return 1; }
      if (f.json) console.log(JSON.stringify(nodes, null, 1));
      else nodes.forEach(function (n) { console.log(formatNode(n)); });
      console.error("[" + nodes.length + " 个节点]");
      return 0;
    }
    if (cmd === "dump") {
      const nodes = d.dump({ save: f.save, mode: f.mode, all: f.all });
      const shown = f.filter
        ? nodes.filter(function (n) {
            return (n.text || "").indexOf(f.filter) >= 0 || (n.desc || "").indexOf(f.filter) >= 0 || n.rid.indexOf(f.filter) >= 0;
          })
        : nodes;
      if (f.json) console.log(JSON.stringify(shown, null, 1));
      else shown.forEach(function (n) { console.log(formatNode(n)); });
      console.error("[" + shown.length + " 个节点" + (f.filter ? "，过滤自 " + nodes.length : "") + "]");
      return 0;
    }
    if (cmd === "find") {
      const found = filterNodes(d.dump({ mode: f.mode, all: f.all }), sel);
      if (!found.length) { console.error("未找到：" + JSON.stringify(sel)); return 1; }
      if (f.json) console.log(JSON.stringify(found, null, 1));
      else found.forEach(function (n) { console.log(formatNode(n)); });
      return 0;
    }
    if (cmd === "tap") {
      const xy = f.pos[1];
      if (xy && /^\d+,\d+$/.test(xy)) {
        const r = d.tap(xy);
        console.log(f.json ? JSON.stringify(r) : "已点 " + r.x + "," + r.y);
        return 0;
      }
      if (!Object.keys(sel).length) { console.error('tap 需要 --rid/--text/--desc/--cls 或 "x,y"'); return 2; }
      const node = d.find(sel);
      if (!node) { console.error("未找到，未点击：" + JSON.stringify(sel)); return 1; }
      d.tap(node);
      console.log(f.json ? JSON.stringify({ tapped: node }) : "已点 " + formatNode(node));
      return 0;
    }
    if (cmd === "type") {
      if (!f.pos[1]) { console.error("type 需要文本（中文需设备装 ADBKeyboard）"); return 2; }
      d.type(f.pos[1]);
      console.log("已输入：" + f.pos[1]);
      return 0;
    }
    if (cmd === "key") {
      if (!f.pos[1]) { console.error("key 需要键名，例如 KEYCODE_BACK"); return 2; }
      d.key(f.pos[1]);
      console.log("已按键 " + f.pos[1]);
      return 0;
    }
    if (cmd === "swipe") {
      const v = f.pos.slice(1).map(Number);
      if (v.length < 4) { console.error("swipe 需要 x1 y1 x2 y2 [ms]"); return 2; }
      d.swipe(v[0], v[1], v[2], v[3], v[4]);
      console.log("已滑动");
      return 0;
    }
    if (cmd === "start") {
      if (!f.pos[1]) { console.error("start 需要 Activity，例如 .activity.settings.SettingGroupActivity"); return 2; }
      const r = d.amstart(f.pos[1], f.extras);
      console.log(f.json ? JSON.stringify({ status: r.status, totalTime: r.totalTime, waitTime: r.waitTime })
                         : "Status=" + r.status + " TotalTime=" + (r.totalTime || "?"));
      return 0;
    }
    if (cmd === "open") {
      if (!f.pos[1]) { console.error("open 需要类名，例如 SettingGroupActivity"); return 2; }
      console.log(d.open(f.pos[1], f.pos[2]));
      return 0;
    }
    if (cmd === "wait") {
      if (!Object.keys(sel).length) { console.error("wait 需要 --rid/--text/--desc/--cls"); return 2; }
      const t0 = Date.now();
      const node = await d.waitFor(sel, { timeout: f.timeout });
      console.log("等到（" + (Date.now() - t0) + " ms）：" + formatNode(node));
      return 0;
    }
    if (cmd === "shot") {
      const r = d.shot(f.pos[1] || "shot.png");
      console.log("已存 " + r.file + "（" + r.bytes + " 字节）");
      return 0;
    }
    if (cmd === "get") {
      if (!f.pos[1]) { console.error("get 需要 key"); return 2; }
      console.log(d.get(f.pos[1]));
      return 0;
    }
    if (cmd === "set") {
      if (!f.pos[1] || f.pos[2] === undefined) { console.error("set 需要 key 和 value"); return 2; }
      console.log(d.set(f.pos[1], f.pos[2], f.type));
      return 0;
    }
    if (cmd === "log") { console.log(d.log({ file: f.file, bytes: f.bytes })); return 0; }
    if (cmd === "logcat") { process.stdout.write(d.logcat(f.tag, f.lines)); return 0; }
    if (cmd === "trail") { console.log(d.trail()); return 0; }

    console.error("未知命令：" + cmd + "\n\n" + HELP);
    return 2;
  } finally {
    if (f.verbose) console.error("--- 耗时 ---\n" + d.report());
  }
}

const invokedDirectly = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (invokedDirectly) {
  main(process.argv.slice(2))
    .then(function (code) { process.exit(code || 0); })
    .catch(function (e) { console.error(String(e && e.message ? e.message : e)); process.exit(1); });
}
