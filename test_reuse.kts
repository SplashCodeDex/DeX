import java.net.*

fun main() {
    val listener = MulticastSocket(null).apply {
        reuseAddress = true
        bind(InetSocketAddress(48424))
    }
    
    val sender = MulticastSocket(null).apply {
        reuseAddress = true
        bind(InetSocketAddress(48424))
    }
    
    println("Successfully bound two sockets to 48424 with reuseAddress!")
    
    listener.close()
    sender.close()
}
