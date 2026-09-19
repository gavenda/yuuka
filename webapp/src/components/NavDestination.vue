<script setup lang="ts">
/** One destination in the phone's bottom bar: an icon in a pill that fills when it is current, with its label beneath. */
defineProps<{
	to: string;
	label: string;
	/** A Material icon path in a 24-unit box. */
	icon: string;
}>();
</script>

<template>
	<!-- `custom`, so the anchor itself can read `isActive` for its own classes. -->
	<RouterLink v-slot="{ href, navigate, isActive }" :to="to" custom>
		<a
			:href="href"
			class="focus-ring group flex flex-1 flex-col items-center gap-1 rounded-lg text-on-surface-variant"
			:aria-current="isActive ? 'page' : undefined"
			@click="navigate"
		>
			<span
				class="grid h-8 w-16 place-items-center rounded-full transition-colors"
				:class="isActive ? 'bg-secondary-container text-on-secondary-container' : 'group-hover:bg-on-surface/8'"
			>
				<svg viewBox="0 0 24 24" class="h-6 w-6" fill="currentColor" aria-hidden="true">
					<path :d="icon" />
				</svg>
			</span>
			<span class="type-label-medium" :class="isActive ? 'text-on-surface' : ''">{{ label }}</span>
		</a>
	</RouterLink>
</template>
