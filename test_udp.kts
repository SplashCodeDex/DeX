import java.net.*

println("Listening for UDP multicast on 224.0.0.167:48424...")
val socket = MulticastSocket(48424)
socket.joinGroup(InetAddress.getByName("224.0.0.167"))

val buffer = ByteArray(2048)
while (true) {
    val packet = DatagramPacket(buffer, buffer.size)
    socket.receive(packet)
    val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
    println("Received from $($packet.address.hostAddress): $msg")
}
