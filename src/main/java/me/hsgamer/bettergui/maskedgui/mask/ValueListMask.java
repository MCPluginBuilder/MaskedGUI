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
package me.hsgamer.bettergui.maskedgui.mask;

import io.github.projectunified.craftux.common.Button;
import io.github.projectunified.craftux.common.Element;
import io.github.projectunified.craftux.mask.ButtonPaginatedMask;
import io.github.projectunified.maptemplate.MapTemplate;
import io.github.projectunified.minelib.scheduler.common.task.Task;
import me.hsgamer.bettergui.builder.ButtonBuilder;
import me.hsgamer.bettergui.builder.RequirementBuilder;
import me.hsgamer.bettergui.maskedgui.builder.MaskBuilder;
import me.hsgamer.bettergui.maskedgui.util.ButtonUtil;
import me.hsgamer.bettergui.maskedgui.util.MaskSlotUtil;
import me.hsgamer.bettergui.maskedgui.util.RequirementUtil;
import me.hsgamer.bettergui.requirement.RequirementApplier;
import me.hsgamer.bettergui.requirement.type.ConditionRequirement;
import me.hsgamer.bettergui.util.SchedulerUtil;
import me.hsgamer.bettergui.util.TickUtil;
import me.hsgamer.hscore.common.CollectionUtils;
import me.hsgamer.hscore.common.MapUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class ValueListMask<T> extends WrappedPaginatedMask<ButtonPaginatedMask> {
    private final Map<T, ValueEntry<T>> valueEntryMap = new ConcurrentHashMap<>();
    private final Map<UUID, ValueListCache> playerListCacheMap = new ConcurrentHashMap<>();
    private final Function<Runnable, Task> scheduler;
    protected long valueUpdateMillis = 1000L;
    protected long viewerUpdateMillis = 50L;
    private Map<String, Object> templateButton = Collections.emptyMap();
    private List<String> viewerConditionTemplate = Collections.emptyList();
    private Map<String, Object> viewerRequirementTemplate = Collections.emptyMap();
    private Task updateTask;

    protected ValueListMask(Function<Runnable, Task> scheduler, MaskBuilder.Input input) {
        super(input);
        this.scheduler = scheduler;
    }

    protected ValueListMask(MaskBuilder.Input input) {
        super(input);
        this.scheduler = runnable -> SchedulerUtil.async().runTimer(runnable, 0, valueUpdateMillis, TimeUnit.MILLISECONDS);
    }

    protected abstract Object replace(String input, T value);

    protected abstract Stream<T> getValueStream();

    protected abstract String getValueIndicator();

    protected String getValueAsString(T value) {
        return Objects.toString(value);
    }

    protected abstract boolean isValueActivated(T value);

    protected abstract boolean canViewValue(UUID uuid, T value);

    protected void configure(Map<String, Object> section) {
        // EMPTY
    }

    private boolean canView(UUID uuid, ValueEntry<T> valueEntry) {
        return valueEntry.activated.get() && canViewValue(uuid, valueEntry.value) && valueEntry.viewPredicate.test(uuid);
    }

    private ValueEntry<T> newValueEntry(T value) {
        MapTemplate mapTemplate = MapTemplate.builder()
                .setVariableFunction(input -> replace(input, value))
                .build();

        Map<String, Object> replacedButtonSettings = MapUtils.castOptionalStringObjectMap(mapTemplate.apply(templateButton)).orElse(templateButton);
        Button button = ButtonBuilder.INSTANCE.build(new ButtonBuilder.Input(getMenu(), String.join("_", getName(), getValueIndicator(), getValueAsString(value), "button"), replacedButtonSettings))
                .<Button>map(ButtonUtil.CraftUXButton::new)
                .orElseGet(() -> (uuid, item) -> false);
        Element.handleIfElement(button, Element::init);

        Predicate<UUID> viewerPredicate = uuid -> true;

        if (!viewerConditionTemplate.isEmpty()) {
            List<String> replacedViewerConditions = CollectionUtils.createStringListFromObject(mapTemplate.apply(viewerConditionTemplate));
            ConditionRequirement viewerCondition = new ConditionRequirement(new RequirementBuilder.Input(getMenu(), "condition", String.join("_", getName(), getValueIndicator(), getValueAsString(value), "condition"), replacedViewerConditions));
            viewerPredicate = viewerPredicate.and(uuid -> viewerCondition.check(uuid).isSuccess);
        }

        if (!viewerRequirementTemplate.isEmpty()) {
            Map<String, Object> replacedViewerRequirements = MapUtils.castOptionalStringObjectMap(mapTemplate.apply(viewerRequirementTemplate)).orElse(viewerRequirementTemplate);
            RequirementApplier viewerRequirementApplier = new RequirementApplier(getMenu(), String.join("_", getName(), getValueIndicator(), getValueAsString(value), "viewer"), replacedViewerRequirements);
            viewerPredicate = viewerPredicate.and(uuid -> RequirementUtil.check(uuid, viewerRequirementApplier));
        }

        return new ValueEntry<>(value, button, viewerPredicate);
    }

    private List<Button> getPlayerButtons(UUID uuid) {
        return playerListCacheMap.compute(uuid, (u, cache) -> {
            long now = System.currentTimeMillis();
            if (cache != null) {
                long remaining = now - cache.lastUpdate;
                if (remaining < viewerUpdateMillis) {
                    return cache;
                }
            }
            return new ValueListCache(
                    now,
                    getValueStream()
                            .map(valueEntryMap::get)
                            .filter(Objects::nonNull)
                            .filter(entry -> canView(uuid, entry))
                            .map(entry -> entry.button)
                            .collect(Collectors.toList())
            );
        }).buttonList;
    }

    @Override
    protected ButtonPaginatedMask createPaginatedMask(Map<String, Object> section) {
        templateButton = Optional.ofNullable(MapUtils.getIfFound(section, "template", "button"))
                .flatMap(MapUtils::castOptionalStringObjectMap)
                .orElse(Collections.emptyMap());
        viewerConditionTemplate = Optional.ofNullable(MapUtils.getIfFound(section, "viewer-condition"))
                .map(CollectionUtils::createStringListFromObject)
                .orElse(Collections.emptyList());
        viewerRequirementTemplate = Optional.ofNullable(MapUtils.getIfFound(section, "viewer-requirement"))
                .flatMap(MapUtils::castOptionalStringObjectMap)
                .orElse(Collections.emptyMap());
        viewerUpdateMillis = Optional.ofNullable(MapUtils.getIfFound(section, "viewer-update-ticks", "viewer-update"))
                .map(String::valueOf)
                .flatMap(TickUtil::toMillis)
                .filter(n -> n > 0)
                .orElse(50L);
        valueUpdateMillis = Optional.ofNullable(MapUtils.getIfFound(section, "value-update-ticks", "value-update"))
                .map(String::valueOf)
                .flatMap(TickUtil::toMillis)
                .filter(n -> n > 0)
                .orElse(50L);
        this.configure(section);
        return new ButtonPaginatedMask(MaskSlotUtil.of(section, this)) {
            @Override
            public @NotNull List<Button> getButtons(@NotNull UUID uuid) {
                return getPlayerButtons(uuid);
            }
        };
    }

    @Override
    public void init() {
        super.init();
        updateTask = scheduler.apply(this::updateValueList);
    }

    @Override
    public void stop() {
        super.stop();
        if (updateTask != null) {
            updateTask.cancel();
        }
        valueEntryMap.values().forEach(playerEntry -> Element.handleIfElement(playerEntry.button, Element::stop));
        valueEntryMap.clear();
    }

    private void updateValueList() {
        getValueStream().forEach(value -> valueEntryMap.compute(value, (currentValue, currentEntry) -> {
            if (currentEntry == null) {
                currentEntry = newValueEntry(value);
            }
            currentEntry.activated.lazySet(isValueActivated(value));
            return currentEntry;
        }));
    }

    private static class ValueEntry<T> {
        final T value;
        final Button button;
        final Predicate<UUID> viewPredicate;
        final AtomicBoolean activated = new AtomicBoolean();

        private ValueEntry(T value, Button button, Predicate<UUID> viewPredicate) {
            this.value = value;
            this.button = button;
            this.viewPredicate = viewPredicate;
        }
    }

    private static class ValueListCache {
        final long lastUpdate;
        final List<Button> buttonList;

        private ValueListCache(long lastUpdate, List<Button> buttonList) {
            this.lastUpdate = lastUpdate;
            this.buttonList = buttonList;
        }
    }
}
