package com.nisovin.shopkeepers.shopobjects.sign;

import java.util.function.Predicate;

import org.bukkit.Material;

import com.nisovin.shopkeepers.compat.MC_1_16_Utils;

public enum SignType {

	// Previously persisted as 'GENERIC'.
	OAK(Material.OAK_SIGN, Material.OAK_WALL_SIGN),
	// Previously persisted as 'REDWOOD'.
	SPRUCE(Material.SPRUCE_SIGN, Material.SPRUCE_WALL_SIGN),
	BIRCH(Material.BIRCH_SIGN, Material.BIRCH_WALL_SIGN),
	JUNGLE(Material.JUNGLE_SIGN, Material.JUNGLE_WALL_SIGN),
	ACACIA(Material.ACACIA_SIGN, Material.ACACIA_WALL_SIGN),
	DARK_OAK(Material.DARK_OAK_SIGN, Material.DARK_OAK_WALL_SIGN),
	CRIMSON(MC_1_16_Utils.getCrimsonSign(), MC_1_16_Utils.getCrimsonWallSign()),
	WARPED(MC_1_16_Utils.getWarpedSign(), MC_1_16_Utils.getWarpedWallSign());

	public static final Predicate<SignType> IS_SUPPORTED = SignType::isSupported;

	// These can be null if the current server version does not support the specific sign type.
	private final Material signMaterial;
	private final Material wallSignMaterial;

	private SignType(Material signMaterial, Material wallSignMaterial) {
		this.signMaterial = signMaterial;
		this.wallSignMaterial = wallSignMaterial;
		// Assert: Either both are null or both are non-null.
		assert signMaterial != null ^ wallSignMaterial == null;
	}

	public boolean isSupported() {
		return (signMaterial != null);
	}

	public Material getSignMaterial() {
		return signMaterial;
	}

	public Material getWallSignMaterial() {
		return wallSignMaterial;
	}
}
