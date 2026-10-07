<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import ColourWheel from '@/components/ColourWheel.vue';
import TextField from '@/components/TextField.vue';
import { ADD } from '@/lib/icons';
import { PALETTE } from '@/lib/palette';
import { computed } from 'vue';
import { t } from '@/i18n';

/**
 * A category's or a tag's colour: one of the palette's, or a hand-picked one. The palette is a row of
 * swatches; the last swatch leaves it for a colour of the person's own, which opens the wheel and a field
 * for the hex itself. `id` is the key the form's validation knows the hex field by.
 */
defineProps<{ id: string; error?: string | null }>();
const emit = defineEmits<{ touch: [] }>();

const model = defineModel<string>({ required: true });

/** True once the colour has strayed from the validated palette onto a hand-picked hex. */
const isCustom = computed(() => !PALETTE.some((slot) => slot.light.toLowerCase() === model.value.toLowerCase()));
const isValid = computed(() => /^#[0-9a-fA-F]{6}$/.test(model.value));

function pick(colour: string): void {
	model.value = colour;
	emit('touch');
}
</script>

<template>
	<fieldset class="flex min-w-0 flex-col gap-3.5">
		<legend class="type-label-medium pb-3.5 text-on-surface">{{ t('common.colour') }}</legend>

		<div class="flex flex-wrap gap-2" role="radiogroup" :aria-label="t('common.colour')">
			<button
				v-for="slot in PALETTE"
				:key="slot.light"
				type="button"
				role="radio"
				class="focus-ring size-8 cursor-pointer rounded-full"
				:class="model.toLowerCase() === slot.light ? 'border-2 border-on-surface' : ''"
				:style="{ backgroundColor: slot.light }"
				:aria-label="t(`form.colours.${slot.name.toLowerCase()}`)"
				:aria-checked="model.toLowerCase() === slot.light"
				:title="t(`form.colours.${slot.name.toLowerCase()}`)"
				@click="pick(slot.light)"
			/>

			<!-- Leaves the palette for a colour of one's own, starting from a neutral grey. -->
			<button
				type="button"
				role="radio"
				class="focus-ring grid size-8 cursor-pointer place-items-center rounded-full text-on-surface"
				:class="isCustom ? 'border-2 border-on-surface' : 'border border-outline-variant bg-surface-container-highest'"
				:style="isCustom && isValid ? { backgroundColor: model } : undefined"
				:aria-label="t('form.customColour')"
				:aria-checked="isCustom"
				:title="t('form.customColour')"
				@click="!isCustom && pick('#64748b')"
			>
				<AppIcon v-if="!isCustom" :icon="ADD" :size="16" />
			</button>
		</div>

		<template v-if="isCustom">
			<ColourWheel v-model="model" />
			<TextField :id="id" v-model="model" :label="t('form.customColourHex')" placeholder="#64748b" :error="error" @blur="emit('touch')" />
		</template>
	</fieldset>
</template>
