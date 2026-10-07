import { createI18n } from 'vue-i18n';
import en from './en';

/**
 * Every word the app shows lives under `i18n/en`, grouped by the screen it belongs to, with what two or more
 * screens say (the verbs of a button, the words of a confirmation) under `common`. Components read it through
 * `$t` / `useI18n`; code with no component around it (validation, the stores, the router) takes `t` from here.
 */
export const i18n = createI18n({
	legacy: false,
	locale: 'en',
	fallbackLocale: 'en',
	messages: { en },
});

export const t = i18n.global.t;
