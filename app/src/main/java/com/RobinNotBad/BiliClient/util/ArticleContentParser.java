package com.RobinNotBad.BiliClient.util;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import com.RobinNotBad.BiliClient.model.ArticleLine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class ArticleContentParser {

    public static final int TYPE_TEXT = 0;
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_HEADING = 2;
    public static final int TYPE_BLOCKQUOTE = 3;
    public static final int TYPE_LIST = 4;
    public static final int TYPE_CODE = 5;
    public static final int TYPE_HR = 6;
    public static final int TYPE_LINK = 7;

    private static final int FONT_SIZE_BILI_DEFAULT = 17;

    public static ArrayList<ArticleLine> parseContent(String jsonContent) {
        ArrayList<ArticleLine> lines = new ArrayList<>();
        if (jsonContent == null || jsonContent.isEmpty()) {
            return lines;
        }

        try {
            JSONArray contentArray = new JSONArray(jsonContent);
            for (int i = 0; i < contentArray.length(); i++) {
                JSONObject block = contentArray.getJSONObject(i);
                String type = block.optString("type", "");
                
                switch (type) {
                    case "paragraph":
                        parseParagraph(block, lines);
                        break;
                    case "image":
                        parseImage(block, lines);
                        break;
                    case "heading":
                        parseHeading(block, lines);
                        break;
                    case "blockquote":
                        parseBlockquote(block, lines);
                        break;
                    case "list":
                        parseList(block, lines);
                        break;
                    case "code":
                        parseCode(block, lines);
                        break;
                    case "hr":
                        lines.add(new ArticleLine(TYPE_HR, "", "hr"));
                        break;
                    case "link":
                        parseLink(block, lines);
                        break;
                    default:
                        lines.add(new ArticleLine(TYPE_TEXT, "[无法识别的类型: " + type + "]", "unknown"));
                }
            }
        } catch (JSONException e) {
            lines.add(new ArticleLine(TYPE_TEXT, jsonContent, "raw"));
        }

        return lines;
    }

    private static void parseParagraph(JSONObject block, ArrayList<ArticleLine> lines) throws JSONException {
        JSONArray children = block.optJSONArray("children");
        if (children == null || children.length() == 0) {
            return;
        }

        StringBuilder textBuilder = new StringBuilder();
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.getJSONObject(i);
            String childType = child.optString("type", "");
            
            switch (childType) {
                case "text":
                    textBuilder.append(child.optString("content", ""));
                    break;
                case "bold":
                case "strong":
                    textBuilder.append(parseBold(child));
                    break;
                case "italic":
                case "em":
                    textBuilder.append(parseItalic(child));
                    break;
                case "link":
                    textBuilder.append(parseLinkText(child));
                    break;
                case "inline_code":
                    textBuilder.append("`").append(parseTextContent(child)).append("`");
                    break;
                default:
                    textBuilder.append(parseTextContent(child));
            }
        }

        String text = textBuilder.toString().trim();
        if (!text.isEmpty()) {
            lines.add(new ArticleLine(TYPE_TEXT, text, "paragraph"));
        }
    }

    private static void parseImage(JSONObject block, ArrayList<ArticleLine> lines) {
        String src = block.optString("src", "");
        if (!src.isEmpty()) {
            if (!src.startsWith("http")) {
                src = "https:" + src;
            }
            lines.add(new ArticleLine(TYPE_IMAGE, src, block.optString("alt", "")));
        }
    }

    private static void parseHeading(JSONObject block, ArrayList<ArticleLine> lines) throws JSONException {
        int level = block.optInt("level", 1);
        JSONArray children = block.optJSONArray("children");
        
        StringBuilder textBuilder = new StringBuilder();
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                JSONObject child = children.getJSONObject(i);
                textBuilder.append(parseTextContent(child));
            }
        }

        String text = textBuilder.toString().trim();
        if (!text.isEmpty()) {
            String prefix = "";
            for (int i = 0; i < level && i < 6; i++) {
                prefix += "#";
            }
            lines.add(new ArticleLine(TYPE_HEADING, prefix + " " + text, "h" + level));
        }
    }

    private static void parseBlockquote(JSONObject block, ArrayList<ArticleLine> lines) throws JSONException {
        JSONArray children = block.optJSONArray("children");
        
        StringBuilder textBuilder = new StringBuilder();
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                JSONObject child = children.getJSONObject(i);
                String childType = child.optString("type", "");
                
                if ("paragraph".equals(childType)) {
                    JSONArray paraChildren = child.optJSONArray("children");
                    if (paraChildren != null) {
                        for (int j = 0; j < paraChildren.length(); j++) {
                            textBuilder.append(parseTextContent(paraChildren.getJSONObject(j)));
                        }
                    }
                } else {
                    textBuilder.append(parseTextContent(child));
                }
            }
        }

        String text = textBuilder.toString().trim();
        if (!text.isEmpty()) {
            lines.add(new ArticleLine(TYPE_BLOCKQUOTE, text, "blockquote"));
        }
    }

    private static void parseList(JSONObject block, ArrayList<ArticleLine> lines) throws JSONException {
        boolean isOrdered = "ordered".equals(block.optString("style", "unordered"));
        JSONArray children = block.optJSONArray("children");
        
        if (children != null) {
            int index = 1;
            for (int i = 0; i < children.length(); i++) {
                JSONObject item = children.getJSONObject(i);
                JSONArray itemChildren = item.optJSONArray("children");
                
                StringBuilder textBuilder = new StringBuilder();
                if (itemChildren != null) {
                    for (int j = 0; j < itemChildren.length(); j++) {
                        JSONObject child = itemChildren.getJSONObject(j);
                        String childType = child.optString("type", "");
                        
                        if ("paragraph".equals(childType)) {
                            JSONArray paraChildren = child.optJSONArray("children");
                            if (paraChildren != null) {
                                for (int k = 0; k < paraChildren.length(); k++) {
                                    textBuilder.append(parseTextContent(paraChildren.getJSONObject(k)));
                                }
                            }
                        } else {
                            textBuilder.append(parseTextContent(child));
                        }
                    }
                }

                String text = textBuilder.toString().trim();
                if (!text.isEmpty()) {
                    String prefix = isOrdered ? (index++) + ". " : "• ";
                    lines.add(new ArticleLine(TYPE_LIST, prefix + text, isOrdered ? "ordered" : "unordered"));
                }
            }
        }
    }

    private static void parseCode(JSONObject block, ArrayList<ArticleLine> lines) {
        String code = block.optString("content", "");
        String language = block.optString("language", "");
        if (!code.isEmpty()) {
            lines.add(new ArticleLine(TYPE_CODE, code, language));
        }
    }

    private static void parseLink(JSONObject block, ArrayList<ArticleLine> lines) {
        String href = block.optString("href", "");
        String text = block.optString("content", href);
        if (!href.isEmpty()) {
            lines.add(new ArticleLine(TYPE_LINK, text, href));
        }
    }

    private static String parseBold(JSONObject node) throws JSONException {
        JSONArray children = node.optJSONArray("children");
        StringBuilder sb = new StringBuilder("**");
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                sb.append(parseTextContent(children.getJSONObject(i)));
            }
        }
        return sb.append("**").toString();
    }

    private static String parseItalic(JSONObject node) throws JSONException {
        JSONArray children = node.optJSONArray("children");
        StringBuilder sb = new StringBuilder("*");
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                sb.append(parseTextContent(children.getJSONObject(i)));
            }
        }
        return sb.append("*").toString();
    }

    private static String parseLinkText(JSONObject node) {
        String href = node.optString("href", "");
        JSONArray children = node.optJSONArray("children");
        StringBuilder sb = new StringBuilder("[");
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                sb.append(parseTextContent(children.optJSONObject(i)));
            }
        }
        return sb.append("](").append(href).append(")").toString();
    }

    private static String parseTextContent(JSONObject node) {
        if (node == null) {
            return "";
        }
        String type = node.optString("type", "");
        if ("text".equals(type)) {
            return node.optString("content", "");
        } else if ("bold".equals(type) || "strong".equals(type)) {
            try {
                return parseBold(node);
            } catch (JSONException e) {
                return "";
            }
        } else if ("italic".equals(type) || "em".equals(type)) {
            try {
                return parseItalic(node);
            } catch (JSONException e) {
                return "";
            }
        } else if ("link".equals(type)) {
            return parseLinkText(node);
        } else if ("inline_code".equals(type)) {
            return "`" + node.optString("content", "") + "`";
        } else {
            JSONArray children = node.optJSONArray("children");
            if (children != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < children.length(); i++) {
                    sb.append(parseTextContent(children.optJSONObject(i)));
                }
                return sb.toString();
            }
        }
        return "";
    }
}