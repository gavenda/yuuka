<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import type { IconPath } from '@/lib/icons';
import { useModalCount } from '@/lib/modal';
import { Dialog } from 'tailwind-material3';

/**
 * Material 3's basic dialog (`AlertDialog` on Android), tailwind-material3's `Dialog`: an optional headline,
 * supporting text and a row of text buttons at the foot — a question that wants an answer before anything else
 * happens, or a short list to pick from. Put the buttons in the `actions` slot, the dismissing one first. It is an
 * `alertdialog` unless told otherwise: pass `role="dialog"` for one that is a list or a couple of fields rather than
 * a question. A dialog that asks for confirmation passes an `icon`: it is drawn above the headline, and the headline
 * is centred under it, as `AlertDialog` does when it is given one. The icon takes the error colour, since what is
 * confirmed is nearly always destructive; pass `:danger="false"` for one that is not. A portrait can take the icon's
 * place (the `icon` slot): it is larger than an icon, so it stands over the headline inside it.
 */
const props = withDefaults(
	defineProps<{ open: boolean; title?: string; icon?: IconPath; danger?: boolean; role?: 'alertdialog' | 'dialog' }>(),
	{
		danger: true,
		role: 'alertdialog',
	},
);
const emit = defineEmits<{ close: [] }>();

useModalCount(() => props.open);
</script>

<template>
	<Dialog :open="open" :role="role" :aria-label="title && !$slots.icon ? undefined : title" @update:open="!$event && emit('close')">
		<template v-if="icon && !$slots.icon" #icon>
			<span :class="danger ? 'text-error' : 'text-secondary'"><AppIcon :icon="icon" /></span>
		</template>
		<template v-if="title || $slots.icon" #headline>
			<span v-if="$slots.icon" class="-mt-2 flex flex-col items-center gap-4 text-center">
				<slot name="icon" />
				<span>{{ title }}</span>
			</span>
			<template v-else>{{ title }}</template>
		</template>
		<div class="sm:min-w-[272px]"><slot /></div>
		<template v-if="$slots.actions" #actions><slot name="actions" /></template>
	</Dialog>
</template>
