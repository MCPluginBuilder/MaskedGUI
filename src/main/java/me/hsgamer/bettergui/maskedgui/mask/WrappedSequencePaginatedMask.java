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
import io.github.projectunified.craftux.mask.SequencePaginatedMask;
import me.hsgamer.bettergui.maskedgui.builder.MaskBuilder;
import me.hsgamer.bettergui.maskedgui.util.ButtonUtil;
import me.hsgamer.bettergui.maskedgui.util.MaskSlotUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class WrappedSequencePaginatedMask extends WrappedPaginatedMask<SequencePaginatedMask> {
    private List<Button> buttons;

    public WrappedSequencePaginatedMask(MaskBuilder.Input input) {
        super(input);
    }

    @Override
    protected SequencePaginatedMask createPaginatedMask(Map<String, Object> section) {
        buttons = ButtonUtil.createChildButtons(this, section).valueStream().<Button>map(ButtonUtil.ButtonWithInput::craftUXButton).collect(Collectors.toList());
        return new SequencePaginatedMask(MaskSlotUtil.of(section, this)) {
            @Override
            public @NotNull List<Button> getButtons(@NotNull UUID uuid) {
                return buttons;
            }
        };
    }

    @Override
    protected void refresh(SequencePaginatedMask mask, UUID uuid) {
        ButtonUtil.refreshCraftUXButtons(uuid, mask.getButtons(uuid));
    }

    @Override
    public void init() {
        super.init();
        Element.handleIfElement(buttons, Element::init);
    }

    @Override
    public void stop() {
        Element.handleIfElement(buttons, Element::stop);
        super.stop();
    }
}
