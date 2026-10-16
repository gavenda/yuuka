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
	type IconPath,
} from '@/lib/icons';
import { computed } from 'vue';

export interface NavLink {
	to: string;
	label: string;
	icon: IconPath;
	iconSelected?: IconPath;
}

/** The daily destinations: the rail's row and the phone's bottom bar (`RAIL_DESTINATIONS` on Android). */
export const dailyLinks = computed<NavLink[]>(() => [
	{ to: '/', label: t('nav.dashboard'), icon: DASHBOARD, iconSelected: DASHBOARD_FILL },
	{ to: '/transactions', label: t('nav.transactions'), icon: RECEIPT, iconSelected: RECEIPT_FILL },
	{ to: '/accounts', label: t('nav.accounts'), icon: ACCOUNT_BALANCE_WALLET, iconSelected: ACCOUNT_BALANCE_WALLET_FILL },
]);

/**
 * Everything else, behind the rail's "More" group and, on a phone, the avatar menu: the less-visited ledger
 * screens, then what is set up once and left alone. Save the Change sits with the screens, as in the Android
 * drawer, and Settings is a screen too.
 */
export const moreLinks = computed<NavLink[]>(() => [
	{ to: '/budget', label: t('nav.budget'), icon: PIE_CHART, iconSelected: PIE_CHART_FILL },
	{ to: '/categories', label: t('nav.categories'), icon: CATEGORY, iconSelected: CATEGORY_FILL },
	{ to: '/tags', label: t('nav.tags'), icon: SELL, iconSelected: SELL_FILL },
	{ to: '/subscriptions', label: t('nav.subscriptions'), icon: EVENT_REPEAT, iconSelected: EVENT_REPEAT_FILL },
	{ to: '/save-the-change', label: t('nav.saveTheChange'), icon: SAVINGS, iconSelected: SAVINGS_FILL },
	{ to: '/settings', label: t('nav.settings'), icon: SETTINGS, iconSelected: SETTINGS_FILL },
]);
