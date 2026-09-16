<script setup lang="ts">
import StatCard from '@/components/StatCard.vue';
import { computeNetPay } from '@/lib/philippinesTax';
import { computed } from 'vue';

/** Gross monthly pay in centavos — the figure being typed into the planned income field. */
const props = defineProps<{ gross: number }>();

const breakdown = computed(() => computeNetPay(props.gross));
</script>

<template>
	<div class="mt-4">
		<p class="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">Monthly contributions</p>
		<div class="mt-2 grid gap-4 sm:grid-cols-3">
			<StatCard v-for="line in breakdown.contributions" :key="line.label" :label="line.label" :amount="line.amount" currency="PHP" />
			<StatCard label="Withholding tax" :amount="breakdown.incomeTax" currency="PHP" />
		</div>
	</div>

	<div class="mt-4">
		<StatCard label="Net pay" :amount="breakdown.netPay" currency="PHP" caption="Used as planned income" />
	</div>
</template>
