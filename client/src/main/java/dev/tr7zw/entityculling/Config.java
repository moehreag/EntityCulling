package dev.tr7zw.entityculling;

import io.github.axolotlclient.AxolotlClientConfig.annotation.annotations.SerializedName;

@io.github.axolotlclient.AxolotlClientConfig.annotation.annotations.Config(name = "entityculling")
public class Config {

	@SerializedName("disable_entity_culling")
	public Boolean disableEntityCulling = false;


	@SerializedName("disable_block_entity_culling")
	public Boolean disableBlockEntityCulling = false;


	@SerializedName("show_f3_info")
	public Boolean showF3Info = true;


	@SerializedName("glass_culls")
	public Boolean glassCulls = false;

}
