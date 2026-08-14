package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.VideoFolder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 文件夹管理器，负责文件夹的增删改查和持久化
 * 数据存储在视频下载目录下的 .folders_info.json 文件中
 */
object FolderManager {

    private const val FOLDERS_FILE = ".folders_info.json"

    /**
     * 获取文件夹信息文件的路径
     */
    private fun getFoldersFile(): File {
        return File(FileUtil.getVideoDownloadPath(), FOLDERS_FILE)
    }

    /**
     * 获取所有文件夹列表
     */
    @JvmStatic
    fun getAllFolders(): ArrayList<VideoFolder> {
        val folders = ArrayList<VideoFolder>()
        val file = getFoldersFile()
        if (!file.exists()) return folders

        try {
            val content = file.readText()
            if (content.isBlank()) return folders
            val jsonArray = JSONArray(content)
            for (i in 0 until jsonArray.length()) {
                folders.add(VideoFolder.fromJson(jsonArray.getJSONObject(i)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return folders
    }

    /**
     * 保存所有文件夹到文件
     */
    private fun saveAllFolders(folders: List<VideoFolder>) {
        try {
            val jsonArray = JSONArray()
            for (folder in folders) {
                jsonArray.put(folder.toJson())
            }
            val file = getFoldersFile()
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 创建新文件夹
     * @return true 成功，false 失败（名称重复或名称无效）
     */
    @JvmStatic
    fun createFolder(name: String): Boolean {
        if (!isValidFolderName(name)) return false

        val folders = getAllFolders()
        // 检查重名
        if (folders.any { it.name == name }) return false

        val newFolder = VideoFolder(name)
        folders.add(newFolder)
        saveAllFolders(folders)
        return true
    }

    /**
     * 重命名文件夹
     * @return true 成功，false 失败
     */
    @JvmStatic
    fun renameFolder(oldName: String, newName: String): Boolean {
        if (!isValidFolderName(newName)) return false
        if (oldName == newName) return true

        val folders = getAllFolders()
        // 检查新名称是否已存在
        if (folders.any { it.name == newName }) return false

        val folder = folders.find { it.name == oldName } ?: return false

        // 更新文件夹名称
        folder.name = newName
        saveAllFolders(folders)

        // 更新所有属于该文件夹的视频元数据
        for (title in folder.videoTitles) {
            VideoMetaManager.updateFolderName(title, newName)
        }

        return true
    }

    /**
     * 删除文件夹（拆散，将视频移回未分类）
     */
    @JvmStatic
    fun deleteFolder(name: String): Boolean {
        val folders = getAllFolders()
        val folder = folders.find { it.name == name } ?: return false

        // 将所有视频的文件夹属性清空
        for (title in folder.videoTitles) {
            VideoMetaManager.updateFolderName(title, "")
        }

        folders.remove(folder)
        saveAllFolders(folders)
        return true
    }

    /**
     * 将视频添加到文件夹
     */
    @JvmStatic
    fun addVideoToFolder(title: String, folderName: String): Boolean {
        val folders = getAllFolders()
        val folder = folders.find { it.name == folderName } ?: return false

        // 先从旧文件夹移除
        removeVideoFromAnyFolder(title, folders)

        folder.addVideo(title)
        saveAllFolders(folders)
        VideoMetaManager.updateFolderName(title, folderName)
        return true
    }

    /**
     * 将视频从文件夹移出到未分类
     */
    @JvmStatic
    fun removeVideoFromFolder(title: String): Boolean {
        val folders = getAllFolders()
        val result = removeVideoFromAnyFolder(title, folders)
        if (result) {
            saveAllFolders(folders)
            VideoMetaManager.updateFolderName(title, "")
        }
        return result
    }

    private fun removeVideoFromAnyFolder(title: String, folders: ArrayList<VideoFolder>): Boolean {
        for (folder in folders) {
            if (folder.removeVideo(title)) {
                return true
            }
        }
        return false
    }

    /**
     * 获取文件夹内的视频标题列表
     */
    @JvmStatic
    fun getFolderVideos(folderName: String): List<String> {
        val folders = getAllFolders()
        val folder = folders.find { it.name == folderName }
        return folder?.videoTitles ?: emptyList()
    }

    /**
     * 验证文件夹名称是否合法
     */
    @JvmStatic
    fun isValidFolderName(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.length > 30) return false
        // 不允许包含特殊字符
        val invalidChars = Regex("[/\\\\:*?\"<>|]")
        if (invalidChars.containsMatchIn(name)) return false
        return true
    }
}