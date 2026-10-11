package dev.auxy.warbcraft.plugin.data;

import org.bukkit.Location;

/** Bir yerlesimin dunyadaki merkezi ve etki yaricapi. */
public record SettlementSite(String settlementId, String world, double x, double y, double z, int radius) {

    public boolean contains(Location location) {
        if (location.getWorld() == null || !location.getWorld().getName().equals(world)) {
            return false;
        }
        double dx = location.getX() - x;
        double dz = location.getZ() - z;
        return dx * dx + dz * dz <= (double) radius * radius;
    }
}
