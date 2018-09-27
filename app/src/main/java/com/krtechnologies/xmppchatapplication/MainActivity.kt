package com.krtechnologies.xmppchatapplication

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jivesoftware.smack.packet.Presence


class MainActivity : AppCompatActivity(), AnkoLogger {

    private lateinit var xmppConnectionService: XMPPConnectionService
    private var isBound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intent = Intent(this, XMPPConnectionService::class.java)
        bindService(intent, myConnection, Context.BIND_AUTO_CREATE)

        val intentFilter = IntentFilter()
        intentFilter.addAction(XMPPConnection.STATE_CONNECTED)
        intentFilter.addAction(XMPPConnection.EXTRA_MESSAGE)
        intentFilter.addAction(XMPPConnection.EXTRA_USER_STATUS)
        registerReceiver(broadcastReceiver, intentFilter)

        btnConnect.setOnClickListener { _ ->
            //xmppConnectionService.sendMessage("kamran1@localhost", "Hi")
            if (isBound)
                xmppConnectionService.start(etUsername.text.toString().trim(), etPassword.text.toString().trim())
        }

        btnPresence.setOnClickListener {
            xmppConnectionService.setStatus(Presence.Type.unavailable, "busy")
        }
    }

    private val myConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName,
                                        service: IBinder) {
            val binder = service as XMPPConnectionService.MyLocalBinder
            xmppConnectionService = binder.getService()

            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            info { "OK RECEIVED" }
            intent?.let {
                when (it.action) {
                    XMPPConnection.STATE_CONNECTED -> {
                        if (xmppConnectionService.getState() == XMPPConnection.ConnectionState.CONNECTED)
                            tvState.text = "Connected"
                    }
                    XMPPConnection.EXTRA_MESSAGE -> {
                        val message = it.extras.getString(XMPPConnection.EXTRA_MESSAGE)
                        info { message }
                        tvMessage.text = message
                    }
                    XMPPConnection.EXTRA_USER_STATUS -> {
                        val isOnline = it.extras.getBoolean(XMPPConnection.EXTRA_USER_STATUS)
                        info { "USER IS $isOnline" }
                    }
                }
            }

        }

    }

}
