package com.krtechnologies.xmppchatapplication

import android.content.Context
import android.content.Intent
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.annotations.Nullable
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.ConnectionListener
import org.jivesoftware.smack.XMPPConnection
import java.lang.Exception
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jivesoftware.smack.ReconnectionManager
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.chat.Chat
import org.jivesoftware.smack.chat.ChatManager
import org.jivesoftware.smack.chat.ChatMessageListener
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.roster.RosterEntry
import org.jivesoftware.smack.roster.RosterListener
import org.jivesoftware.smackx.chatstates.ChatState
import org.jivesoftware.smackx.chatstates.ChatStateListener
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest
import org.jivesoftware.smackx.receipts.ReceiptReceivedListener


/**
 * Created by ingizly on 9/27/18
 **/
class XMPPConnection(context: Context, val username: String, val password: String) : ConnectionListener, AnkoLogger, ReceiptReceivedListener {

    companion object {
        val STATE_CONNECTED = "com.krtechnologies.xmppchatapplication.STATE_CONNECTED"
        val EXTRA_MESSAGE = "com.krtechnologies.xmppchatapplication.EXTRA_MESSAGE"
        val EXTRA_USER_STATUS = "com.krtechnologies.xmppchatapplication.EXTRA_USER_STATUS"
    }

    enum class ConnectionState {
        CONNECTED, AUTHENTICATED, CONNECTING, DISCONNECTING, DISCONNECTED
    }

    enum class LoggedInState {
        LOGGED_IN, LOGGED_OUT
    }

    private var mApplicationContext: Context? = null
    private var mConnection: XMPPTCPConnection? = null

    init {
        this.mApplicationContext = context
    }

    fun connect() {
        info { "Connecting to server" }
        val config = XMPPTCPConnectionConfiguration.builder()
                .setUsernameAndPassword(username, password)
                .setHost("10.0.2.2")
                .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
                .setServiceName("localhost")
                .setPort(5222)
                .setDebuggerEnabled(true) // to view what's happening in detail
                .build()

        //Set up the ui thread broadcast message receiver.
        //setupUiThreadBroadCastMessageReceiver();

        mConnection = XMPPTCPConnection(config)
        mConnection?.addConnectionListener(this)

        val reconnectionManager = ReconnectionManager.getInstanceFor(mConnection)
        reconnectionManager.enableAutomaticReconnection()

        mConnection?.connect()
        mConnection?.login()
    }

    fun disconnect() {
        info { "Disconnecting from server" }
        try {
            mConnection?.disconnect()

        } catch (e: SmackException.NotConnectedException) {
            XMPPConnectionService.sConnectionState = ConnectionState.DISCONNECTED
            e.printStackTrace()
        }
        mConnection = null
    }

    override fun connected(connection: XMPPConnection?) {
        XMPPConnectionService.sConnectionState = ConnectionState.CONNECTED
        info { "Connected" }
    }

    override fun connectionClosed() {
        XMPPConnectionService.sConnectionState = ConnectionState.DISCONNECTED
        info { "Connection Closed" }
    }

    override fun connectionClosedOnError(e: Exception?) {
        XMPPConnectionService.sConnectionState = ConnectionState.DISCONNECTED
        info { "Connection Closed On Error" }
    }

    override fun reconnectionSuccessful() {
        XMPPConnectionService.sConnectionState = ConnectionState.CONNECTED
        info { "Reconnect Successful" }
    }

    override fun authenticated(connection: XMPPConnection?, resumed: Boolean) {
        XMPPConnectionService.sConnectionState = ConnectionState.CONNECTED
        info { "Authenticated" }

        sendBroadcast(STATE_CONNECTED)

        val chatManager = ChatManager.getInstanceFor(connection)
        chatManager.addChatListener { chat, _ ->
            chat.addMessageListener { _, message ->
                val messageXml = message.toXML().toString()

                message?.let { message1 ->
                    if (message1.body != null) {
                        sendBroadcast(EXTRA_MESSAGE, message1.body)
                    }
                }
            }
        }
        connection?.addAsyncStanzaListener({

            val element = it.getExtension(ChatStateExtension.NAMESPACE)
            if (element != null) {
                when (element.elementName) {
                    "composing" -> info { "typing..." }
                    "paused" -> info { "paused" }
                    "active" -> info { "active" }
                    "inactive" -> info { "inactive" }
                    "gone" -> info { "gone" }
                }
            }
        }, null)

        DeliveryReceiptManager.getInstanceFor(connection).addReceiptReceivedListener(this)
        val roster = Roster.getInstanceFor(connection)
        roster.subscriptionMode = Roster.SubscriptionMode.accept_all
        roster.addRosterListener(object : RosterListener {
            override fun entriesDeleted(addresses: MutableCollection<String>?) {

            }

            override fun presenceChanged(presence: Presence?) {
                try {
                    val friend = roster.getEntry(presence?.from)
                    if (friend == null) {
                        val pres = Presence(Presence.Type.subscribed)
                        pres.to = presence?.from
                        mConnection?.sendStanza(pres)
                    } else sendBroadcast(EXTRA_USER_STATUS, null, presence?.isAvailable)
                } catch (e: SmackException.NotConnectedException) {
                    e.printStackTrace()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }

            }

            override fun entriesUpdated(addresses: MutableCollection<String>?) {

            }

            override fun entriesAdded(addresses: MutableCollection<String>?) {
            }

        })
        //roster.createEntry("kamran1@localhost", "Kamran", null)

        val friend = roster.getEntry("$username@localhost")
        if (friend != null) {
            info { "NAME OF FRIEND ${friend.name} AND STATUS: ${roster.getPresence(friend.user).isAvailable}" }
            sendBroadcast(EXTRA_USER_STATUS, null, roster.getPresence(friend.user).isAvailable)
        } else
            info { "IS SUBSCRIBED ${roster.isSubscribedToMyPresence("$username@localhost")}" }
    }

    override fun reconnectionFailed(e: Exception?) {
        XMPPConnectionService.sConnectionState = ConnectionState.DISCONNECTED
        info { "Reconnect Failed" }
    }

    override fun reconnectingIn(seconds: Int) {
        XMPPConnectionService.sConnectionState = ConnectionState.CONNECTING
        info { "Reconnecting in $seconds seconds" }
    }

    private fun sendBroadcast(state: String, message: String? = "", isOnline: Boolean? = false) {
        val intent = Intent(state)
        intent.setPackage(mApplicationContext?.packageName)
        intent.putExtra(EXTRA_MESSAGE, message)
        intent.putExtra(EXTRA_USER_STATUS, isOnline)
        mApplicationContext?.sendBroadcast(intent)
    }

    fun sendMessage(to: String, message: String) {
        val mess = Message(to)
        mess.body = message
        val manager = ChatManager.getInstanceFor(mConnection)
        val deliveryReceiptId = DeliveryReceiptRequest.addTo(mess)
        manager.createChat(to, MessageListener()).sendMessage(mess)
    }


    override fun onReceiptReceived(fromJid: String?, toJid: String?, receiptId: String?, receipt: Stanza?) {
        info {
            "onReceiptReceived: from: $fromJid to: $toJid deliveryReceiptId: $receiptId stanza: $receipt"
        }

    }

    fun setStatus(status: Presence.Type, presenceStatus: String) {
        val presence = Presence(status)
        presence.status = presenceStatus
        mConnection?.sendStanza(presence)
    }

    inner class MessageListener : ChatMessageListener, ChatStateListener {
        override fun processMessage(chat: Chat?, message: Message?) {
            info { "MESSAGE ${message?.body}" }
        }

        override fun stateChanged(chat: Chat?, state: ChatState?) {
            if (ChatState.composing.equals(chat)) {
                info { "${chat?.participant} is typing.." }
            } else if (ChatState.gone.equals(state)) {
                info { "${chat?.participant} is typing.." }
            } else {
                info { "Chat State , ${chat?.participant}  ${state?.name}" }
            }
        }

    }
}