/*
   Copyright 2023-2024 Huynh Tien

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
package me.hsgamer.bettergui.maskedgui.mask;

import io.github.projectunified.craftux.mask.ButtonPaginatedMask;
import me.hsgamer.bettergui.api.button.BaseWrappedButton;
import me.hsgamer.bettergui.api.button.WrappedButton;
import me.hsgamer.bettergui.maskedgui.builder.MaskBuilder;
import me.hsgamer.bettergui.maskedgui.util.ButtonUtil;
import me.hsgamer.bettergui.maskedgui.util.MaskSlotUtil;
import me.hsgamer.bettergui.maskedgui.util.RequirementUtil;
import me.hsgamer.bettergui.requirement.RequirementApplier;
import me.hsgamer.bettergui.util.TickUtil;
import me.hsgamer.hscore.collections.map.CaseInsensitiveStringLinkedMap;
import me.hsgamer.hscore.common.MapUtils;
import me.hsgamer.hscore.minecraft.gui.button.Button;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class FilteredButtonPaginatedMask extends WrappedPaginatedMask<ButtonPaginatedMask> {
    private final List<ButtonWithFilter> buttonWithFilterList = new ArrayList<>();
    private final Map<UUID, ButtonListCache> playerListCacheMap = new ConcurrentHashMap<>();
    private long updateMillis = 0L;

    public FilteredButtonPaginatedMask(MaskBuilder.Input input) {
        super(input);
    }

    private List<Button> getPlayerButtons(UUID uuid) {
        return playerListCacheMap.compute(uuid, (u, cache) -> {
            long now = System.currentTimeMillis();
            if (cache != null) {
                long remaining = now - cache.lastUpdate;
                if (remaining < updateMillis) {
                    return cache;
                }
            }

            List<Button> buttonList = buttonWithFilterList.stream()
                    .filter(buttonWithFilter -> {
                        RequirementApplier filterRequirementApplier = buttonWithFilter.filterRequirementApplier;
                        return filterRequirementApplier == null || RequirementUtil.check(uuid, filterRequirementApplier);
                    })
                    .map(buttonWithFilter -> buttonWithFilter.button)
                    .collect(Collectors.toList());
            return new ButtonListCache(now, buttonList);
        }).buttonList;
    }

    @Override
    protected ButtonPaginatedMask createPaginatedMask(Map<String, Object> section) {
        updateMillis = Optional.ofNullable(section.get("update"))
                .map(String::valueOf)
                .flatMap(TickUtil::toMillis)
                .filter(n -> n > 0)
                .orElse(50L);

        ButtonUtil.createChildButtons(this, section)
                .valueStream()
                .peek(buttonWithInput -> buttonWithInput.button.init())
                .map(buttonWithInput -> {
                    Map<String, Object> options;
                    if (buttonWithInput.button instanceof BaseWrappedButton<?>) {
                        options = new CaseInsensitiveStringLinkedMap<>(((BaseWrappedButton<?>) buttonWithInput.button).getOptions());
                    } else {
                        options = buttonWithInput.input.options;
                    }
                    RequirementApplier filterRequirementApplier = Optional.ofNullable(MapUtils.getIfFound(options, "filter-requirement"))
                            .flatMap(MapUtils::castOptionalStringObjectMap)
                            .map(map -> new RequirementApplier(getMenu(), buttonWithInput.input.name + "_filter", map))
                            .orElse(null);
                    return new ButtonWithFilter(buttonWithInput.button, filterRequirementApplier);
                })
                .forEach(buttonWithFilterList::add);

        return new ButtonPaginatedMask(MaskSlotUtil.of(section, this)) {
            @Override
            public @NotNull List<io.github.projectunified.craftux.common.Button> getButtons(@NotNull UUID uuid) {
                return getPlayerButtons(uuid).stream().map(ButtonUtil.CraftUXButton::new).collect(Collectors.toList());
            }
        };
    }

    private static final class ButtonWithFilter {
        private final WrappedButton button;
        private final @Nullable RequirementApplier filterRequirementApplier;

        private ButtonWithFilter(WrappedButton button, @Nullable RequirementApplier filterRequirementApplier) {
            this.button = button;
            this.filterRequirementApplier = filterRequirementApplier;
        }
    }

    private static class ButtonListCache {
        final long lastUpdate;
        final List<Button> buttonList;

        private ButtonListCache(long lastUpdate, List<Button> buttonList) {
            this.lastUpdate = lastUpdate;
            this.buttonList = buttonList;
        }
    }
}
