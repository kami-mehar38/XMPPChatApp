package com.krtechnologies.xmppchatapplication

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.packet.Presence
import java.io.IOException


class XMPPConnectionService : Service(), AnkoLogger {

    private val myBinder = MyLocalBinder()

    companion object {
        var sConnectionState: XMPPConnection.ConnectionState? = null
        var sLoggedInState: XMPPConnection.LoggedInState? = null
    }

    private var mActive: Boolean = false //Stores whether or not the thread is active
    private var mThread: Thread? = null
    private var mTHandler: Handler? = null
    private var mConnection: XMPPConnection? = null

    override fun onBind(intent: Intent): IBinder? {
        info { "Service Binded" }
        return myBinder
    }

    override fun onCreate() {
        super.onCreate()
        info { "Service Created" }
    }

    private fun initConnection(username: String, password: String) {
        info { "initConnection()" }
        if (mConnection == null) {
            mConnection = XMPPConnection(this, username, password)
        }
        try {
            mConnection?.connect()

        } catch (e: IOException) {
            info { "Something went wrong while connecting ,make sure the credentials are right and try again" }
            e.printStackTrace()
            //Stop the service all together.
            stopSelf()
        } catch (e: SmackException) {
            info { "Something went wrong while connecting ,make sure the credentials are right and try again" }
            e.printStackTrace()
            stopSelf()
        } catch (e: XMPPException) {
            info { "Something went wrong while connecting ,make sure the credentials are right and try again" }
            e.printStackTrace()
            stopSelf()
        }

    }

    fun start(username: String, password: String) {
        info { "Service Start() function called." }
        if (!mActive) {
            mActive = true
            if (mThread == null || !mThread?.isAlive!!) {
                mThread = Thread(Runnable {
                    Looper.prepare()
                    mTHandler = Handler()
                    initConnection(username, password)
                    //THE CODE HERE RUNS IN A BACKGROUND THREAD.
                    Looper.loop()
                })
                mThread?.start()
            }
        }
    }

    fun stop() {
        info { "stop()" }
        mActive = false
        mTHandler?.post {
            if (mConnection != null)
                mConnection?.disconnect()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        info { "onStartCommand()" }

        return Service.START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        info { "onDestroy()" }
        stop()
    }

    fun getState(): XMPPConnection.ConnectionState? {
        return if (sConnectionState == null) {
            XMPPConnection.ConnectionState.DISCONNECTED
        } else sConnectionState
    }

    fun getLoggedInState(): XMPPConnection.LoggedInState? {
        return if (sLoggedInState == null) {
            XMPPConnection.LoggedInState.LOGGED_OUT
        } else sLoggedInState
    }

    inner class MyLocalBinder : Binder() {
        fun getService(): XMPPConnectionService {
            return this@XMPPConnectionService
        }
    }

    fun sendMessage(to: String, message: String) {
        mConnection?.sendMessage(to, message)
    }

    fun setStatus(status: Presence.Type, presenceStatus: String) {
        mConnection?.setStatus(status, presenceStatus)
    }
}
