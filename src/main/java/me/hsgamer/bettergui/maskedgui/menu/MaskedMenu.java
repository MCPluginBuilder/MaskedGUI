/*
   Copyright 2023-2023 Huynh Tien

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package me.hsgamer.bettergui.maskedgui.menu;

import io.github.projectunified.craftux.common.ActionItem;
import io.github.projectunified.craftux.common.Position;
import io.github.projectunified.craftux.mask.HybridMask;
import me.hsgamer.bettergui.maskedgui.api.signal.Signal;
import me.hsgamer.bettergui.maskedgui.builder.MaskBuilder;
import me.hsgamer.bettergui.maskedgui.util.MaskUtil;
import me.hsgamer.bettergui.menu.BaseInventoryMenu;
import me.hsgamer.hscore.collections.map.CaseInsensitiveStringMap;
import me.hsgamer.hscore.common.MapUtils;
import me.hsgamer.hscore.config.Config;
import me.hsgamer.hscore.minecraft.gui.button.ButtonMap;
import me.hsgamer.hscore.minecraft.gui.button.DisplayButton;
import me.hsgamer.hscore.minecraft.gui.object.InventoryPosition;
import me.hsgamer.hscore.minecraft.gui.object.InventorySize;
import me.hsgamer.hscore.minecraft.gui.object.Item;
import org.bukkit.event.inventory.InventoryType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

public class MaskedMenu extends BaseInventoryMenu<MaskedMenu.CraftUXButtonMap> {
    public MaskedMenu(Config config) {
        super(config);
    }

    @Override
    protected CraftUXButtonMap createButtonMap() {
        CraftUXButtonMap buttonMap = new CraftUXButtonMap();
        for (Map.Entry<String, Object> entry : configSettings.entrySet()) {
            String key = entry.getKey();
            Optional<Map<String, Object>> optionalValue = MapUtils.castOptionalStringObjectMap(entry.getValue());
            if (!optionalValue.isPresent()) continue;
            Map<String, Object> value = optionalValue.get();
            Map<String, Object> values = new CaseInsensitiveStringMap<>(value);
            MaskBuilder.INSTANCE
                    .build(new MaskBuilder.Input(this, "mask_" + key, values))
                    .ifPresent(mask -> {
                        mask.init();
                        buttonMap.hybridMask.add(mask);
                    });
        }
        return buttonMap;
    }

    @Override
    protected void refreshButtonMapOnCreate(CraftUXButtonMap buttonMap, UUID uuid) {
        MaskUtil.refreshMasks(uuid, buttonMap.hybridMask.getElements());
    }

    public void handleSignal(UUID uuid, Signal signal) {
        CraftUXButtonMap buttonMap = getButtonMap();
        if (buttonMap == null) return;
        MaskUtil.handleSignal(uuid, buttonMap.hybridMask.getElements(), signal);
    }

    public int getSlotPerRow() {
        InventoryType inventoryType = getGUIHolder().getInventoryType();
        switch (inventoryType) {
            case CHEST:
            case ENDER_CHEST:
            case SHULKER_BOX:
                return 9;
            case DISPENSER:
            case DROPPER:
            case HOPPER:
                return 3;
            default:
                return 0;
        }
    }

    public InventorySize makeFakeInventorySize() {
        return new InventorySize() {
            @Override
            public int getSize() {
                return 54;
            }

            @Override
            public int getSlotPerRow() {
                return MaskedMenu.this.getSlotPerRow();
            }
        };
    }

    public static class CraftUXButtonMap implements ButtonMap {
        private final HybridMask hybridMask = new HybridMask();

        @Override
        public @NotNull Map<@NotNull Integer, @NotNull DisplayButton> getButtons(@NotNull UUID uuid, InventorySize inventorySize) {
            Map<Position, ActionItem> map = hybridMask.getActionMap(uuid);
            if (map == null) return Collections.emptyMap();
            Map<Integer, @NotNull DisplayButton> buttonMap = new HashMap<>();
            for (Map.Entry<Position, ActionItem> entry : map.entrySet()) {
                Position position = entry.getKey();
                ActionItem actionItem = entry.getValue();

                int slot = InventoryPosition.of(position.getX(), position.getY()).toSlot(inventorySize);

                DisplayButton displayButton = new DisplayButton();
                Object item = actionItem.getItem();
                if (item instanceof Item) {
                    displayButton.setItem((Item) item);
                }
                Consumer<Object> action = actionItem.getAction();
                if (action != null) {
                    displayButton.setAction(action::accept);
                }

                buttonMap.put(slot, displayButton);
            }
            return buttonMap;
        }
    }
}
