# KubeJS mechanism coverage (auto-generated)

Source: `tools/kubejs_mechanism_audit.py` — scans every Prism instance with a `minecraft/kubejs` folder. Regenerate after a pack update.

- Packs scanned: **14**
- Distinct mechanisms (call names seen on item-id lines): **863**
- Distinct custom recipe classes: **21**
- Distinct event families: **86**

## Mechanisms on item-id lines (role table seed)

| name | sites | packs | example |
|---|---|---|---|
| `tag` | 12261 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_food_eaten.js:5` |
| `Item.of` | 6096 | 12 | `AI_test_NFWC_DIM/client_scripts/golden_age/jei.js:14` |
| `texture` | 4247 | 10 | `AI_test_NFWC_DIM/startup_scripts/curios_register.js:4` |
| `Organ` | 3779 | 3 | `AI_test_NFWC_DIM/startup_scripts/organ_register.js:24` |
| `event.create` | 3425 | 9 | `AI_test_NFWC_DIM/client_scripts/ponder/ponder_scenes.js:3` |
| `player.potionEffects.add` | 3171 | 5 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_constdef.js:188` |
| `event.remove` | 1943 | 14 | `AI_test_NFWC_DIM/server_scripts/b_a_d/recipe/common.js:2` |
| `if` | 1578 | 10 | `AI_test_NFWC_DIM/client_scripts/dlc/add_tooltip.js:18` |
| `Ingredient.of` | 1575 | 10 | `AI_test_NFWC_DIM/client_scripts/jei.js:13` |
| `input` | 1535 | 5 | `AI_test_NFWC_DIM/server_scripts/b_a_d/ritual/summoning_rituals.js:8` |
| `typeMap.has` | 1350 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_food_eaten.js:151` |
| `typeMap.get` | 1204 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_key_bind.js:25` |
| `itemMap.has` | 1201 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_alice_in_hell.js:7` |
| `player.addItemCooldown` | 1153 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_alice_in_hell.js:497` |
| `player.hasEffect` | 1117 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_key_bind.js:308` |
| `allthemods.add` | 1019 | 3 | `All the Mods 10 - ATM10/client_scripts/Mekanism-Tooltips.js:14` |
| `player.getZ` | 1006 | 2 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_key_bind.js:134` |
| `event.add` | 1000 | 13 | `AI_test_NFWC_DIM/server_scripts/b_a_d/holy_trinity/the_ritual.js:126` |
| `id` | 989 | 9 | `AI_test_NFWC_DIM/server_scripts/b_a_d/ritual/summoning_rituals.js:5` |
| `event.shaped` | 869 | 11 | `AI_test_NFWC_DIM/server_scripts/b_a_d/recipe/common.js:154` |
| `event.recipes.createDeploying` | 776 | 5 | `AI_test_NFWC_DIM/server_scripts/b_a_d/recipe/create.js:36` |
| `entity.potionEffects.add` | 739 | 5 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_alice_in_hell.js:80` |
| `tooltip.addAdvanced` | 730 | 4 | `AI_test_NFWC_DIM/client_scripts/hpdlc_organ_tooltips.js:3` |
| `modifyAttribute` | 649 | 3 | `AI_test_NFWC_DIM/startup_scripts/item_register.js:137` |
| `Fluid.of` | 605 | 9 | `AI_test_NFWC_DIM/server_scripts/momo_dlc/hecheng/momo_dlc_create.js:57` |
| `create.deploying` | 592 | 2 | `AI_test_NFWC_DIM/server_scripts/hpdlc_peifang/create_peifang.js:31` |
| `altar` | 591 | 5 | `AI_test_NFWC_DIM/server_scripts/b_a_d/ritual/summoning_rituals.js:4` |
| `LootEntry.of` | 573 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/item/b_a_d_world_loot.js:12` |
| `transitionalItem` | 533 | 5 | `AI_test_NFWC_DIM/server_scripts/b_a_d/recipe/create.js:40` |
| `allthemods.remove` | 533 | 2 | `All the Mods 10 - ATM10/client_scripts/RecipeViewer.js:16` |
| `player.getEffect` | 513 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_key_bind.js:309` |
| `player.removeEffect` | 475 | 5 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_constdef.js:202` |
| `event.addItem` | 448 | 3 | `AI_test_NFWC_DIM/client_scripts/jei.js:8` |
| `player.getAttributeTotalValue` | 445 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_food_eaten.js:57` |
| `itemOutput` | 443 | 4 | `AI_test_NFWC_DIM/server_scripts/b_a_d/ritual/summoning_rituals.js:10` |
| `addLootTableModifier` | 420 | 2 | `龙之冒险：新征程v2.1版本/server_scripts/LootTable.js:134` |
| `itemIn` | 410 | 2 | `All the Mods 10 - ATM10/server_scripts/modpack/runic_multis/controllers.js:72` |
| `event.replaceOutput` | 391 | 4 | `Create- Astral/server_scripts/server.js:119` |
| `DefaultOrgan` | 372 | 3 | `AI_test_NFWC_DIM/client_scripts/default_organ_tooltips.js:147` |
| `addModifier` | 364 | 2 | `AI_test_NFWC_DIM/server_scripts/golden_age/gate.js:152` |
| `player.getMainHandItem` | 357 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_constdef.js:195` |
| `song` | 347 | 3 | `AI_test_NFWC_DIM/startup_scripts/music_disc_register.js:12` |
| `e.create` | 340 | 2 | `AI_test_NFWC_DIM/startup_scripts/ino_dlc_build/ino_dlc_build_item_register.js:3` |
| `player.removeAttribute` | 332 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_alice_in_hell.js:463` |
| `ResourceLocation` | 326 | 2 | `AI_test_NFWC_DIM/server_scripts/dlc/dlc_update.js:6` |
| `event.replaceInput` | 311 | 8 | `AI_test_NFWC_DIM/server_scripts/dlc/dlc_recipe.js:16` |
| `player.modifyAttribute` | 308 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_alice_in_hell.js:460` |
| `event.recipes.create.deploying` | 296 | 3 | `AI_test_NFWC_DIM/server_scripts/golden_age/ink_create.js:539` |
| `ItemEvents.rightClicked` | 280 | 8 | `AI_test_NFWC_DIM/server_scripts/b_a_d/item/b_a_d_item.js:3` |
| `itemMap.get` | 256 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_key_bind.js:1103` |
| `event.recipes.create.mechanical_crafting` | 250 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/recipe/create.js:16` |
| `event.shapeless` | 246 | 9 | `AI_test_NFWC_DIM/server_scripts/b_a_d/recipe/common.js:20` |
| `addDefaultAttribute` | 237 | 3 | `AI_test_NFWC_DIM/startup_scripts/golden_age/dlc_template_item_register.js:258` |
| `entity.hasEffect` | 230 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_player_bear.js:564` |
| `addEntities` | 224 | 2 | `AI_test_NFWC_DIM/server_scripts/golden_age/gate.js:139` |
| `entity.level.createEntity` | 220 | 2 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_key_bind.js:858` |
| `scene.world.setBlock` | 211 | 5 | `All the Mods 10 - ATM10/client_scripts/ponder/fission_mek.js:86` |
| `BioForgingRecipe` | 203 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/recipe/bio_forging.js:33` |
| `addAttribute` | 202 | 2 | `AI_test_NFWC_DIM/server_scripts/mrqx_extra_pack/mrqx_data_loader/mrqx_tetra_improvement.js:17` |
| `food.effect` | 189 | 3 | `AI_test_NFWC_DIM/startup_scripts/item_register.js:33` |
| `event.recipes.createPressing` | 187 | 5 | `AI_test_NFWC_DIM/server_scripts/b_a_d/recipe/create.js:46` |
| `event.recipes.custommachinery.custom_machine` | 175 | 4 | `AI_test_NFWC_DIM/server_scripts/recipes/organ_recycler/organ_to_coin.js:3` |
| `entity.getEffect` | 155 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_player_damage.js:580` |
| `contains` | 142 | 2 | `AI_test_NFWC_DIM/client_scripts/mrqx_extra_pack/mrqx_jei.js:54` |
| `event.recipes.createCutting` | 142 | 5 | `AI_test_NFWC_DIM/server_scripts/b_a_d/recipe/create.js:37` |
| `hasTag` | 141 | 4 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_food_eaten.js:183` |
| `event.addEntityLootModifier` | 135 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/item/b_a_d_world_loot.js:16` |
| `removeLoot` | 130 | 4 | `AI_test_NFWC_DIM/server_scripts/b_a_d/item/b_a_d_world_loot.js:123` |
| `allthemods.shaped` | 130 | 2 | `All the Mods 10 - ATM10/server_scripts/modpack/atm_alloys.js:7` |
| `posMap.get` | 129 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_organ_active.js:163` |
| `event.entity.potionEffects.add` | 121 | 5 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_player_damage.js:243` |
| `addAdditionalAttribute` | 120 | 3 | `AI_test_NFWC_DIM/startup_scripts/item_register.js:159` |
| `event.recipes.create.filling` | 117 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/recipe/create.js:48` |
| `entity.removeEffect` | 116 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_key_bind.js:67` |
| `player.give` | 113 | 3 | `AI_test_NFWC_DIM/server_scripts/b_a_d/b_a_d_alice_in_hell.js:513` |
| `pool.addItem` | 111 | 6 | `AI_test_NFWC_DIM/server_scripts/hpdlc/hpdlc_diaoluo.js:7` |
| `createEntity` | 109 | 3 | `AI_test_NFWC_DIM/server_scripts/golden_age/events.js:361` |
| `event.recipes.create.pressing` | 104 | 2 | `AI_test_NFWC_DIM/server_scripts/golden_age/recipes.js:364` |
| `setTab` | 104 | 2 | `AI_test_NFWC_DIM/server_scripts/momo_dlc/hecheng/momo_dlc_xuerou.js:37` |
| `event.modify` | 99 | 7 | `AI_test_NFWC_DIM/startup_scripts/item_modify.js:3` |

## Custom recipe classes (`new *Recipe(...)`) — invisible to JEI unless the mod ships a plugin

| name | sites | packs | example |
|---|---|---|---|
| `GoetyRitualRecipe` | 374 |  |  |
| `BioForgingRecipe` | 311 | 3 |  |
| `DragonForgeRecipe` | 127 |  |  |
| `MomoCookingRecipe` | 78 |  |  |
| `CookingRecipe` | 70 |  |  |
| `MixingCauldronRecipe` | 69 |  |  |
| `BioBrewingRecipe` | 45 |  |  |
| `mrqxGoetyRitualRecipe` | 28 |  |  |
| `WeaponInfusionRecipe` | 21 |  |  |
| `DecomposingRecipe` | 15 |  |  |
| `DigestingRecipe` | 13 |  |  |
| `mrqxBioForgingRecipe` | 12 |  |  |
| `BADCookingRecipe` | 6 |  |  |
| `mrqxCookingRecipe` | 6 |  |  |
| `CuttingRecipe` | 6 |  |  |
| `DryingRackRecipe` | 6 |  |  |
| `mrqxDigestingRecipe` | 4 |  |  |
| `RollingRecipe` | 3 |  |  |
| `goldenageMixingBowlRecipe` | 2 |  |  |
| `mrqxCapsidRecipe` | 2 |  |  |
| `mrqxMixingBowlRecipe` | 2 |  |  |

## register*Recipe functions

| name | sites | packs | example |
|---|---|---|---|
| `registerCustomRecipe` | 1447 |  |  |
| `maodlcRegisterCustomRecipe` | 4 |  |  |
| `registerCapsidRecipe` | 4 |  |  |

## Event families

| name | sites | packs | example |
|---|---|---|---|
| `ServerEvents.recipes` | 485 |  |  |
| `PlayerEvents.tick` | 325 |  |  |
| `ItemEvents.rightClicked` | 314 | 8 |  |
| `StartupEvents.registry` | 249 | 5 |  |
| `ServerEvents.tags` | 148 | 2 |  |
| `ForgeEvents.onEvent` | 108 |  |  |
| `BlockEvents.rightClicked` | 96 | 8 |  |
| `EntityEvents.death` | 84 | 3 |  |
| `EntityEvents.hurt` | 78 | 2 |  |
| `ItemEvents.foodEaten` | 60 | 5 |  |
| `ItemEvents.tooltip` | 58 |  |  |
| `ServerEvents.highPriorityData` | 57 |  |  |
| `BlockEvents.broken` | 47 | 3 |  |
| `EntityEvents.spawned` | 38 | 1 |  |
| `NetworkEvents.dataReceived` | 37 |  |  |
| `PlayerEvents.loggedIn` | 37 |  |  |
| `PlayerEvents.spellOnCast` | 32 |  |  |
| `JEIEvents.information` | 26 |  |  |
| `MMREvents.machines` | 26 |  |  |
| `ItemEvents.firstLeftClicked` | 24 | 2 |  |
| `ServerEvents.generateData` | 24 |  |  |
| `ClientEvents.tick` | 19 |  |  |
| `PlayerEvents.inventoryClosed` | 19 |  |  |
| `PlayerEvents.loggedOut` | 17 |  |  |
| `ServerEvents.loaded` | 15 |  |  |
| `PlayerEvents.respawned` | 15 |  |  |
| `ItemEvents.modification` | 14 |  |  |
| `ForgeModEvents.onEvent` | 13 |  |  |
| `StartupEvents.init` | 13 |  |  |
| `ServerEvents.commandRegistry` | 12 |  |  |
| `ClientEvents.init` | 12 |  |  |
| `StartupEvents.postInit` | 11 |  |  |
| `NativeEvents.onEvent` | 10 |  |  |
| `ClientEvents.highPriorityAssets` | 8 |  |  |
| `PlayerEvents.inventoryChanged` | 8 |  |  |
| `MIMachineEvents.registerRecipeTypes` | 8 |  |  |
| `MIMachineEvents.registerMachines` | 8 |  |  |
| `JEIEvents.hideItems` | 7 |  |  |
| `ServerEvents.entityLootTables` | 7 |  |  |
| `ItemEvents.entityInteracted` | 7 |  |  |
| `BlockEvents.placed` | 6 | 1 |  |
| `ChestCavityEvents.updateOrganScore` | 6 |  |  |
| `ServerEvents.fishingLootTables` | 6 |  |  |
| `MoreJSEvents.villagerTrades` | 5 |  |  |
| `MoreJSEvents.enchantmentTableChanged` | 5 |  |  |
| `JEIEvents.addItems` | 5 |  |  |
| `ClientEvents.loggedIn` | 4 |  |  |
| `PlayerEvents.changeMana` | 4 |  |  |
| `ServerEvents.tick` | 4 |  |  |
| `ItemEvents.pickedUp` | 4 |  |  |
| `KeyBindEvents.register` | 4 |  |  |
| `ItemEvents.modifyTooltips` | 4 |  |  |
| `RecipeViewerEvents.removeEntriesCompletely` | 4 | 2 |  |
| `RecipeViewerEvents.addInformation` | 4 |  |  |
| `StartupEvents.modifyCreativeTab` | 4 | 4 |  |
| `BlockEvents.leftClicked` | 4 | 1 |  |
| `MoreJSEvents.enchantmentTableTooltip` | 3 |  |  |
| `MoreJSEvents.filterAvailableEnchantments` | 3 |  |  |
| `MoreJSEvents.filterEnchantedBookTrade` | 3 |  |  |
| `EntityEvents.checkSpawn` | 3 |  |  |
| `RenderJSEvents.AddWorldRender` | 2 |  |  |
| `MoreJSEvents.wandererTrades` | 2 |  |  |
| `ServerEvents.command` | 2 |  |  |
| `ItemEvents.crafted` | 2 |  |  |
| `PlayerEvents.inventoryOpened` | 2 |  |  |
| `ItemEvents.dropped` | 2 |  |  |
| `MoreJSEvents.enchantmentTableEnchant` | 2 |  |  |
| `ServerEvents.entity` | 2 | 2 |  |
| `ColdSweatEvents.temperatureChanged` | 2 |  |  |
| `RecipeViewerEvents.removeRecipes` | 2 |  |  |
| `RecipeViewerEvents.removeEntries` | 2 |  |  |
| `RecipeViewerEvents.removeCategories` | 2 |  |  |
| `LevelEvents.unloaded` | 2 |  |  |
| `ServerEvents.basicPublicCommand` | 2 |  |  |
| `MIMachineEvents.registerCasings` | 2 |  |  |
| `MIRegistrationEvents.registerCableTiers` | 2 |  |  |
| `MIMachineEvents.registerHatches` | 2 |  |  |
| `JEIEvents.removeCategories` | 1 |  |  |
| `ClientEvents.lang` | 1 |  |  |
| `EntityEvents.drops` | 1 |  |  |

## Item-moving APIs

| name | sites | packs | example |
|---|---|---|---|
| `add(` | 6578 |  |  |
| `addItem` | 1837 |  |  |
| `addLoot` | 1158 | 5 |  |
| `give` | 535 |  |  |
| `popItem` | 136 |  |  |
| `TradeItem.of` | 54 | 3 |  |
| `RECIPES.add` | 50 |  |  |
| `setItemSlot` | 33 |  |  |
| `setMainHandItem` | 24 |  |  |
| `setItem` | 14 |  |  |
| `setStackInSlot` | 6 |  |  |
| `addDrop` | 3 | 2 |  |

## Packs scanned

| pack | js | json | item ids | mechanisms | LootJS files |
|---|---|---|---|---|---|
| AI_test_NFWC_DIM | 648 | 3673 | 10344 | 623 | 15 |
| No_Flesh_Within_Chest-1.0.2-DIM | 648 | 3673 | 10344 | 623 | 15 |
| All the Mods 10 - ATM10(1) | 204 | 624 | 3241 | 104 | 0 |
| All the Mods 10 - ATM10 | 203 | 572 | 3232 | 104 | 0 |
| NoFleshWithinChest.Maya.sExpackB0.3.2V1.4.5 | 159 | 826 | 2885 | 188 | 4 |
| Not Too Complicated 2 | 140 | 114 | 4376 | 42 | 0 |
| Multiblock Madness 2 | 85 | 318 | 3352 | 96 | 0 |
| All the Mods 9 - To the Sky - atm9sky | 63 | 173 | 686 | 53 | 0 |
| 神秘启旅客户端 beta 0.6 | 41 | 19 | 1078 | 15 | 1 |
| Project Architect 2 | 36 | 156 | 2687 | 36 | 0 |
| 龙之冒险：新征程v2.1版本 | 31 | 26 | 1263 | 42 | 2 |
| 龙之冒险：新征程v2.2 | 31 | 27 | 1269 | 42 | 2 |
| Create- Milkyway | 11 | 16 | 153 | 28 | 0 |
| Create- Astral | 3 | 14 | 987 | 37 | 0 |
