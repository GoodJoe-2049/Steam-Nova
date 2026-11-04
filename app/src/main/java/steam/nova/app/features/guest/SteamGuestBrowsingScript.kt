package steam.nova.app.features.guest

import android.content.Context
import steam.nova.app.core.ModuleScriptLocalization
import steam.nova.app.core.SteamBundleCompatibility
import steam.nova.app.features.steamguard.ui.SteamGuardNextCodeScript
import steam.nova.app.features.library.SteamLibraryGridScript
import steam.nova.app.features.chat.ui.SteamChatScript
import steam.nova.app.features.profile.betterprofile.SteamBetterProfileScript
import steam.nova.app.features.profile.customization.SteamProfileCustomizationAvatarScript
import steam.nova.app.features.store.banner.SteamStoreBannerScript

/** Small, bundle-specific adapters. Account state and the native login form remain Steam-owned. */
internal object SteamGuestBrowsingScript {
    fun source(
        context: Context,
        bundle: SteamBundleCompatibility.Profile,
        defaultRoute: String,
        nextCodeEnabled: Boolean,
        libraryPinchZoomEnabled: Boolean,
        chatBridgePrefix: String? = null,
        betterProfileEnabled: Boolean = false,
        profileCustomizationBridgePrefix: String? = null,
        animatedStoreBannerEnabled: Boolean = false,
    ): String {
        return """
(function(g) {
    'use strict';
    if (g.__millenniumGuestInstalled) return;
    g.__millenniumGuestInstalled = true;

${bundle.source()}

${if (nextCodeEnabled || chatBridgePrefix != null) ModuleScriptLocalization.source(context) else ""}

    function facade(base, name, value) {
        var result = Object.create(base);
        Object.defineProperty(result, name, { value: value, enumerable: true });
        return result;
    }

    function guestNavigation(base, require) {
        return facade(base, 'useNavigation', function() {
            var navigation = base.useNavigation();
            return require(115).useMemo(function() {
                return facade(navigation, 'navigate', function(name, params) {
                    // Suppress the startup prompt only when there is no active account.
                    // Existing-account reauthentication and explicit login stay native.
                    if (name === 'Modal' && params && params.screen === 'Login' &&
                        params.params && params.params.bCanDismiss === false) {
                        var state = require(1136).useDebugAuthState().GetCurrentState();
                        if (state.bReady && !state.bLoggedIn && !state.activeSteamID) return;
                    }
                    return navigation.navigate.apply(navigation, arguments);
                });
            }, [navigation]);
        });
    }

    function guestHome(base, require) {
        return facade(base, 'useHomeTab', function() {
            var result = base.useHomeTab();
            var auth = require(1136).useAuth();
            // Wait for restoration: a saved account must not be mistaken for a guest.
            if (!auth.bReady) {
                return [Object.assign({}, result[0], { isLoading: true }), result[1]];
            }
            if (!auth.bLoggedIn) {
                return [Object.assign({}, result[0], { data: 'store' }), result[1]];
            }
            return result;
        });
    }

    function guestMenu(base, require) {
        var React = require(115);
        return facade(base, 'ScreenWithHeader', function GuestMenuHeader(props) {
            var auth = require(1136).useAuth();
            var scroll = props.children;
            if (auth.bReady && !auth.bLoggedIn && React.isValidElement(scroll)) {
                var label = require(1450).Localize('#MobileLogin_SignIn');
                var row = React.createElement(require(1590).Box, {
                    key: 'millennium-guest-sign-in',
                    paddingX: 'gutter', paddingY: 'large',
                    accessibilityRole: 'button', accessibilityLabel: label,
                    onPress: function() {
                        props.navigation.navigate('Modal', {
                            screen: 'Login',
                            params: { bCanDismiss: true, bMakeActiveAccount: true }
                        });
                    }
                }, React.createElement(require(1589).Text, { weight: 'strong', tone: 'info' }, label));
                scroll = React.cloneElement(scroll, null, row, scroll.props.children);
            }
            return React.createElement(base.ScreenWithHeader, props, scroll);
        });
    }

    function guestSettingsQuery(base, require) {
        var guestSettings = { settings: { is_enabled: false }, mapAppsAllowed: new Map() };
        return facade(base, 'useQueryFromAsyncStorage', function(key) {
            var result = base.useQueryFromAsyncStorage.apply(base, arguments);
            var auth = require(1136).useAuth();
            // No account has no parental policy. Steam otherwise treats the absent result
            // as loading/blocked, hiding MainTabs and disabling public navigation.
            // Never change a stored query or the policy of an active account.
            if (key[0] === 'ParentalSettings_2' && auth.bReady &&
                !auth.bLoggedIn && !auth.activeSteamID) {
                return Object.assign({}, result, {
                    isLoading: false, data: guestSettings
                });
            }
            return result;
        });
    }

    function publicStoreUrl(value) {
        if (typeof value !== 'string' || /[\s\\]/.test(value)) return null;
        var mobile = /^steammobile:\/\/store\?(.*)/i.exec(value);
        if (mobile) {
            var app = /(?:^|&)appid=([1-9][0-9]*)(?:&|$)/.exec(mobile[1]);
            return app ? 'https://store.steampowered.com/app/' + app[1] + '/' : null;
        }
        var match = /^https:\/\/store\.steampowered\.com(\/[^?#]*)?([?#].*)?$/i.exec(value);
        if (!match) return null;
        var path = match[1] || '/';
        if (/(?:^|\/)\.{1,2}(?:\/|$)|%(?:2e|2f|5c|25)/i.test(path)) return null;
        // Only public browsing destinations. Account, checkout and authentication links
        // continue through Steam's original handler, including Steam Guard links.
        if (path === '/' || /^\/(?:app\/[0-9]+|sub\/[0-9]+|bundle\/[0-9]+|news|search|explore|genre|category|tags|publisher|developer|sale|specials)(?:\/|$)/i.test(path)) {
            return 'https://store.steampowered.com' + path + (match[2] || '');
        }
        return null;
    }

    function guestLinks(exports, require) {
        var original = exports.useLinkNavigationHandling;
        exports.useLinkNavigationHandling = function() {
            original();
            var React = require(115);
            var auth = require(1136).useAuth();
            var store = require(1136).useDebugAuthState();
            var open = exports.useOpenUrl();
            React.useEffect(function() {
                if (!auth.bReady || auth.bLoggedIn) return;
                var box = exports.AppLinkWithNoActiveUser;
                var previous = box.set;
                function set(value) {
                    var state = store.GetCurrentState();
                    var url = state.bReady && !state.bLoggedIn ? publicStoreUrl(value) : null;
                    if (url) {
                        open(url);
                        return previous.call(box, '');
                    }
                    return previous.call(box, value);
                }
                box.set = set;
                var pending = box.get();
                if (pending) set(pending);
                return function() { if (box.set === set) box.set = previous; };
            }, [auth.bReady, auth.bLoggedIn, store, open]);
        };
    }

${if (betterProfileEnabled || profileCustomizationBridgePrefix != null) SteamBetterProfileScript.source(context, betterProfileEnabled, profileCustomizationBridgePrefix) else ""}
${if (profileCustomizationBridgePrefix != null) SteamProfileCustomizationAvatarScript.source() else ""}
${SteamGuardDefaultPageScript.source(defaultRoute)}
${if (nextCodeEnabled) SteamGuardNextCodeScript.source() else ""}
${if (libraryPinchZoomEnabled) SteamLibraryGridScript.source() else ""}
${chatBridgePrefix?.let { SteamChatScript.source(context, it) } ?: ""}
${if (animatedStoreBannerEnabled) SteamStoreBannerScript.source() else ""}

    var targets = { 1136: true, 1476: true, 2125: true, 2135: true, 2412: true, 2455: true };
    if ($nextCodeEnabled) targets[2413] = true;
    if ($libraryPinchZoomEnabled) targets[3466] = true;
    if (${chatBridgePrefix != null}) { targets[1892] = true; targets[2211] = true; }
    if (${betterProfileEnabled || profileCustomizationBridgePrefix != null}) { targets[2176] = true; targets[2210] = true; }
    if (${profileCustomizationBridgePrefix != null}) { targets[1864] = true; targets[1888] = true; }
    if ($animatedStoreBannerEnabled) targets[2176] = true;
    // Only translate our adapter IDs. Steam's factories and dependency arrays
    // continue to receive their own bundle's IDs, including numeric collisions.
    var hostTargets = {};
    Object.keys(targets).forEach(function(id) { hostTargets[steamModuleId(Number(id))] = Number(id); });
    var remaining = Object.keys(hostTargets).length;
    var define;
    function wrapDefine(originalDefine) {
        return function(factory, hostId, dependencies) {
            if (!Object.prototype.hasOwnProperty.call(hostTargets, hostId)) return originalDefine.apply(this, arguments);
            var id = hostTargets[hostId];
            delete hostTargets[hostId];
            var wrapped = function() {
                var args = Array.prototype.slice.call(arguments);
                var hostRequire = args[1];
                function require(adapterId) { return hostRequire(steamModuleId(adapterId)); }
                Object.assign(require, hostRequire);
                var cache = {};
                args[1] = function(dependency) {
                    if (Object.prototype.hasOwnProperty.call(cache, dependency)) return cache[dependency];
                    var value = hostRequire(dependency);
                    if (id === 1136 && dependency === steamModuleId(1366)) value = guestNavigation(value, require);
                    if (id === 1476 && dependency === steamModuleId(3459)) value = guestHome(value, require);
                    if (id === 2125 && dependency === steamModuleId(2126)) value = guestSettingsQuery(value, require);
                    if (id === 2412 && dependency === steamModuleId(1477)) value = guardNavigator(value, require);
                    if (id === 2413 && dependency === steamModuleId(263)) value = guardNextCodeRuntime(value, require);
                    if (id === 2455 && dependency === steamModuleId(2352)) value = guestMenu(value, require);
                    if ($libraryPinchZoomEnabled && id === 3466 && dependency === steamModuleId(263)) value = libraryGridRuntime(value, require);
                    if (${chatBridgePrefix != null} && dependency === steamModuleId(263) && (id === 1476 || id === 1892 || id === 2455)) value = chatNavigationRuntime(value, require, id);
                    if (${chatBridgePrefix != null} && id === 2211 && dependency === steamModuleId(2212)) value = chatStoreHeader(value, require);
                    if (${betterProfileEnabled || profileCustomizationBridgePrefix != null} && id === 2176 && dependency === steamModuleId(263)) value = betterProfileWebRuntime(value, require);
                    if ($animatedStoreBannerEnabled && id === 2176 && dependency === steamModuleId(263)) value = storeBannerWebRuntime(value, require);
                    if (${betterProfileEnabled || profileCustomizationBridgePrefix != null} && id === 2210 && dependency === steamModuleId(2212)) value = betterProfileHeader(value, require);
                    if (${profileCustomizationBridgePrefix != null} && id === 1888 && dependency === steamModuleId(1750)) value = profileAvatarStaticImage(value, require);
                    cache[dependency] = value;
                    return value;
                };
                Object.assign(args[1], hostRequire);
                var result = factory.apply(this, args);
                if (id === 2135) guestLinks(args[4].exports, require);
                if (${profileCustomizationBridgePrefix != null} && id === 1864) profileAvatarPersona(args[4].exports, require);
                return result;
            };
            var result = originalDefine.call(this, wrapped, hostId, dependencies);
            if (--remaining === 0) {
                // The adapters are now registered. All remaining Metro definitions are untouched.
                Object.defineProperty(g, '__d', { value: originalDefine, writable: true, configurable: true });
            }
            return result;
        };
    }
    Object.defineProperty(g, '__d', {
        configurable: true,
        get: function() { return define; },
        set: function(value) { define = wrapDefine(value); }
    });
})(globalThis);
""".trimIndent()
    }
}
