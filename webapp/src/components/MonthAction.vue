<script setup lang="ts">
import AlertDialog from '@/components/AlertDialog.vue';
import AppIcon from '@/components/AppIcon.vue';
import { currentMonth } from '@/lib/dates';
import { KEYBOARD_ARROW_LEFT, KEYBOARD_ARROW_RIGHT } from '@/lib/icons';
import { registerMonthAction } from '@/lib/monthAction';
import { computed, onBeforeUnmount, reactive, ref, toRef, watch } from 'vue';
import { t } from '@/i18n';

/**
 * The month a screen is looking at (`MonthTopBar` in `ScreenTopBar.kt`). It draws no button of its own: the
 * shell does, after the hide-amounts switch — in the phone's bar and, from `sm` up, at the rail's foot (see
 * `App.vue` and `NavRail`) — and pressing either opens the picker here, a year with its twelve months.
 */
const props = defineProps<{ modelValue: string }>();
const emit = defineEmits<{ 'update:modelValue': [string] }>();

const picking = ref(false);
const year = ref(Number(props.modelValue.slice(0, 4)));

const unregister = registerMonthAction(reactive({ month: toRef(props, 'modelValue'), open: () => (picking.value = true) }));
onBeforeUnmount(unregister);

// The picker opens on the year being shown, wherever it was last left.
watch(picking, (open) => {
	if (open) year.value = Number(props.modelValue.slice(0, 4));
});

const months = computed(() =>
	Array.from({ length: 12 }, (_, index) => {
		const value = `${String(year.value).padStart(4, '0')}-${String(index + 1).padStart(2, '0')}`;
		return {
			value,
			label: new Intl.DateTimeFormat(undefined, { month: 'short' }).format(new Date(year.value, index, 1)),
			selected: value === props.modelValue,
			current: value === currentMonth(),
		};
	}),
);

function pick(month: string): void {
	picking.value = false;
	emit('update:modelValue', month);
}
</script>

<template>
	<!-- Material 3 ships a day picker but no month one, so this is a year with arrows over a 3 × 4 grid of
	     month names, styled after the date picker's own year grid. Picking a month closes it at once. -->
	<AlertDialog :open="picking" @close="picking = false">
		<div class="w-[min(100%,20rem)] text-on-surface sm:w-80">
			<div class="flex items-center">
				<button type="button" class="btn-icon m-1" :aria-label="t('common.months.previousYear')" @click="year--">
					<AppIcon :icon="KEYBOARD_ARROW_LEFT" />
				</button>
				<span class="type-title-medium flex-1 text-center" aria-live="polite">{{ year }}</span>
				<button type="button" class="btn-icon m-1" :aria-label="t('common.months.nextYear')" @click="year++">
					<AppIcon :icon="KEYBOARD_ARROW_RIGHT" />
				</button>
			</div>

			<div class="grid grid-cols-3 gap-2 pt-2">
				<button
					v-for="month in months"
					:key="month.value"
					type="button"
					class="state-layer focus-ring type-body-large h-10 cursor-pointer rounded-xl"
					:class="
						month.selected
							? 'bg-primary-container text-on-primary-container'
							: month.current
								? 'border border-outline text-primary'
								: 'text-on-surface'
					"
					:aria-pressed="month.selected"
					@click="pick(month.value)"
				>
					{{ month.label }}
				</button>
			</div>
		</div>

		<template #actions>
			<button type="button" class="btn-text" @click="picking = false">{{ t('common.cancel') }}</button>
		</template>
	</AlertDialog>
</template>
