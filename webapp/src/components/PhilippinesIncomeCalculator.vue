<script setup lang="ts">
import { computeNetPay } from '@/lib/philippinesTax';
import { displayMoney } from '@/lib/privacy';
import { computed } from 'vue';

/** Gross monthly pay in centavos — the figure being typed into the planned income field. */
const props = defineProps<{ gross: number }>();

const breakdown = computed(() => computeNetPay(props.gross));
const lines = computed(() => [...breakdown.value.contributions, { label: 'Withholding tax', amount: breakdown.value.incomeTax }]);
</script>

<template>
	<div class="mt-4">
		<p class="type-title-small text-on-surface-variant">Monthly contributions</p>
		<ul class="mt-2 divide-y divide-outline-variant">
			<li v-for="line in lines" :key="line.label" class="flex items-center justify-between gap-3 py-2 text-sm">
				<span class="text-on-surface-variant">{{ line.label }}</span>
				<span class="tabular text-on-surface">{{ displayMoney(line.amount, 'PHP') }}</span>
			</li>
		</ul>
	</div>
</template>
