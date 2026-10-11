package dev.auxy.warbcraft.core.economy;

import java.util.Objects;

/**
 * Ticaret mali (tahil, demir, sarap, kurk...).
 *
 * @param basePrice hedef stokta iken birim fiyat
 */
public record Good(String id, String name, int basePrice) {

    public Good {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (basePrice <= 0) {
            throw new IllegalArgumentException(id + ": taban fiyat pozitif olmali");
        }
    }
}
