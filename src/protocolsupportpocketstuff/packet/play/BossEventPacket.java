package protocolsupportpocketstuff.packet.play;

import io.netty.buffer.ByteBuf;
import protocolsupport.api.Connection;
import protocolsupport.api.ProtocolVersion;
import protocolsupport.protocol.serializer.StringSerializer;
import protocolsupport.protocol.serializer.VarNumberSerializer;
import protocolsupportpocketstuff.packet.PEPacket;
import protocolsupport.protocol.ConnectionImpl;


public class BossEventPacket extends PEPacket {

	private long entityId;
	private int eventId;

	public static final int SHOW = 0;
	public static final int UPDATE = 1;
	public static final int REMOVE = 2;

	public BossEventPacket() { }

	public BossEventPacket(long entityId, int eventId) {
		this.entityId = entityId;
		this.eventId = eventId;
	}

	@Override
	public int getPacketId() {
		return 74;
	} // Boss Event Packet

	@Override
	public void toData(ConnectionImpl connection, ByteBuf serializer) {
        VarNumberSerializer.writeSVarLong(serializer, entityId);
        VarNumberSerializer.writeVarInt(serializer, eventId);
        switch (eventId) {
            case SHOW: {
                StringSerializer.writeString(serializer, ProtocolVersion.MINECRAFT_PE_1_8, "");
                serializer.writeFloat(1.F);
                serializer.writeShort(0);
                VarNumberSerializer.writeVarInt(serializer, 0);
                VarNumberSerializer.writeVarInt(serializer, 0);
                break;
            }
            case UPDATE: {
                VarNumberSerializer.writeVarLong(serializer, Integer.MAX_VALUE);
                break;
            }
            default:
                break;
        }
    }

	@Override
	public void readFromClientData(ConnectionImpl connection, ByteBuf clientData) { }

}
