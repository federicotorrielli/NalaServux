package mc.nala.servux.syncmatica.material;

import mc.nala.servux.syncmatica.data.ServerPosition;
import net.minecraft.core.BlockPos;

public class DeliveryPosition extends ServerPosition {

    private final int amount;

    public DeliveryPosition(final BlockPos pos, final String dim, final int amount) {
        super(pos, dim);
        this.amount = amount;
    }

    public int getAmount() {
        return amount;
    }
}
