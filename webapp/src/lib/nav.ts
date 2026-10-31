import { t } from '@/i18n';
import {
	ACCOUNT_BALANCE_WALLET,
	ACCOUNT_BALANCE_WALLET_FILL,
	CATEGORY,
	CATEGORY_FILL,
	DASHBOARD,
	DASHBOARD_FILL,
	EVENT_REPEAT,
	EVENT_REPEAT_FILL,
	PIE_CHART,
	PIE_CHART_FILL,
	RECEIPT,
	RECEIPT_FILL,
	SAVINGS,
	SAVINGS_FILL,
	SELL,
	SELL_FILL,
	SETTINGS,
	SETTINGS_FILL,
	TRENDING_UP,
	TRENDING_UP_FILL,
	type IconPath,
} from '@/lib/icons';
import { computed } from 'vue';

export interface NavLink {
	to: string;
	label: string;
	icon: IconPath;
	iconSelected?: IconPath;
}

export interface NavGroup {
	label: string;
	links: NavLink[];
}

/** The daily destinations: the rail's row and the phone's bottom bar (`RAIL_DESTINATIONS` on Android). */
export const dailyLinks = computed<NavLink[]>(() => [
	{ to: '/transactions', label: t('nav.transactions'), icon: RECEIPT, iconSelected: RECEIPT_FILL },
	{ to: '/investments', label: t('nav.investments'), icon: TRENDING_UP, iconSelected: TRENDING_UP_FILL },
	{ to: '/accounts', label: t('nav.accounts'), icon: ACCOUNT_BALANCE_WALLET, iconSelected: ACCOUNT_BALANCE_WALLET_FILL },
]);

/**
 * Everything else, grouped by what it is for (`NAV_GROUPS` on Android): what is read to see how things stand,
 * what posts money by itself, and the labels set up once. The open rail and the phone's avatar menu draw the
 * headings; Settings and Sign out follow without one.
 */
export const navGroups = computed<NavGroup[]>(() => [
	{
		label: t('nav.groupInsights'),
		links: [
			{ to: '/overview', label: t('nav.dashboard'), icon: DASHBOARD, iconSelected: DASHBOARD_FILL },
			{ to: '/budget', label: t('nav.budget'), icon: PIE_CHART, iconSelected: PIE_CHART_FILL },
		],
	},
	{
		label: t('nav.groupAutomations'),
		links: [
			{ to: '/subscriptions', label: t('nav.subscriptions'), icon: EVENT_REPEAT, iconSelected: EVENT_REPEAT_FILL },
			{ to: '/save-the-change', label: t('nav.saveTheChange'), icon: SAVINGS, iconSelected: SAVINGS_FILL },
		],
	},
	{
		label: t('nav.groupOrganize'),
		links: [
			{ to: '/categories', label: t('nav.categories'), icon: CATEGORY, iconSelected: CATEGORY_FILL },
			{ to: '/tags', label: t('nav.tags'), icon: SELL, iconSelected: SELL_FILL },
		],
	},
]);

/** Settings: about the app rather than the money, so it takes no heading. */
export const settingsLink = computed<NavLink>(() => ({
	to: '/settings',
	label: t('nav.settings'),
	icon: SETTINGS,
	iconSelected: SETTINGS_FILL,
}));
