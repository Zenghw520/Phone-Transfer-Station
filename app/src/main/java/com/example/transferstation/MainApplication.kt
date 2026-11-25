package com.example.transferstation

import android.app.Application
import com.example.transferstation.data.repository.ChatRepository

/** 在应用进程中持有跨 Activity 和前台服务共享的聊天仓库。 */
class MainApplication : Application() {
    /** 创建进程级 SQLite、图片目录和生成任务协调器。 */
    override fun onCreate() {
        super.onCreate()
        repository = ChatRepository(this)
    }

    /** 释放进程退出时的数据库及协程作用域。 */
    override fun onTerminate() {
        repository.close()
        super.onTerminate()
    }

    /** 供 Activity 和生成前台服务访问唯一的聊天仓库。 */
    val chatRepository: ChatRepository
        get() = repository

    private lateinit var repository: ChatRepository
}
