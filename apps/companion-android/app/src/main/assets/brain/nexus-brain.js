"use strict";

function _toConsumableArray(r) { return _arrayWithoutHoles(r) || _iterableToArray(r) || _unsupportedIterableToArray(r) || _nonIterableSpread(); }
function _nonIterableSpread() { throw new TypeError("Invalid attempt to spread non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method."); }
function _iterableToArray(r) { if ("undefined" != typeof Symbol && null != r[Symbol.iterator] || null != r["@@iterator"]) return Array.from(r); }
function _arrayWithoutHoles(r) { if (Array.isArray(r)) return _arrayLikeToArray(r); }
function _slicedToArray(r, e) { return _arrayWithHoles(r) || _iterableToArrayLimit(r, e) || _unsupportedIterableToArray(r, e) || _nonIterableRest(); }
function _nonIterableRest() { throw new TypeError("Invalid attempt to destructure non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method."); }
function _iterableToArrayLimit(r, l) { var t = null == r ? null : "undefined" != typeof Symbol && r[Symbol.iterator] || r["@@iterator"]; if (null != t) { var e, n, i, u, a = [], f = !0, o = !1; try { if (i = (t = t.call(r)).next, 0 === l) { if (Object(t) !== t) return; f = !1; } else for (; !(f = (e = i.call(t)).done) && (a.push(e.value), a.length !== l); f = !0); } catch (r) { o = !0, n = r; } finally { try { if (!f && null != t.return && (u = t.return(), Object(u) !== u)) return; } finally { if (o) throw n; } } return a; } }
function _arrayWithHoles(r) { if (Array.isArray(r)) return r; }
function _typeof(o) { "@babel/helpers - typeof"; return _typeof = "function" == typeof Symbol && "symbol" == typeof Symbol.iterator ? function (o) { return typeof o; } : function (o) { return o && "function" == typeof Symbol && o.constructor === Symbol && o !== Symbol.prototype ? "symbol" : typeof o; }, _typeof(o); }
function _createForOfIteratorHelper(r, e) { var t = "undefined" != typeof Symbol && r[Symbol.iterator] || r["@@iterator"]; if (!t) { if (Array.isArray(r) || (t = _unsupportedIterableToArray(r)) || e && r && "number" == typeof r.length) { t && (r = t); var _n = 0, F = function F() {}; return { s: F, n: function n() { return _n >= r.length ? { done: !0 } : { done: !1, value: r[_n++] }; }, e: function e(r) { throw r; }, f: F }; } throw new TypeError("Invalid attempt to iterate non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method."); } var o, a = !0, u = !1; return { s: function s() { t = t.call(r); }, n: function n() { var r = t.next(); return a = r.done, r; }, e: function e(r) { u = !0, o = r; }, f: function f() { try { a || null == t.return || t.return(); } finally { if (u) throw o; } } }; }
function _unsupportedIterableToArray(r, a) { if (r) { if ("string" == typeof r) return _arrayLikeToArray(r, a); var t = {}.toString.call(r).slice(8, -1); return "Object" === t && r.constructor && (t = r.constructor.name), "Map" === t || "Set" === t ? Array.from(r) : "Arguments" === t || /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(t) ? _arrayLikeToArray(r, a) : void 0; } }
function _arrayLikeToArray(r, a) { (null == a || a > r.length) && (a = r.length); for (var e = 0, n = Array(a); e < a; e++) n[e] = r[e]; return n; }
var NexusNativeBrain = function () {
  var __defProp = Object.defineProperty;
  var __defProps = Object.defineProperties;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropDescs = Object.getOwnPropertyDescriptors;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __getOwnPropSymbols = Object.getOwnPropertySymbols;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __propIsEnum = Object.prototype.propertyIsEnumerable;
  var __defNormalProp = function __defNormalProp(obj, key, value) {
    return key in obj ? __defProp(obj, key, {
      enumerable: true,
      configurable: true,
      writable: true,
      value: value
    }) : obj[key] = value;
  };
  var __spreadValues = function __spreadValues(a, b) {
    for (var prop in b || (b = {})) if (__hasOwnProp.call(b, prop)) __defNormalProp(a, prop, b[prop]);
    if (__getOwnPropSymbols) {
      var _iterator = _createForOfIteratorHelper(__getOwnPropSymbols(b)),
        _step;
      try {
        for (_iterator.s(); !(_step = _iterator.n()).done;) {
          var prop = _step.value;
          if (__propIsEnum.call(b, prop)) __defNormalProp(a, prop, b[prop]);
        }
      } catch (err) {
        _iterator.e(err);
      } finally {
        _iterator.f();
      }
    }
    return a;
  };
  var __spreadProps = function __spreadProps(a, b) {
    return __defProps(a, __getOwnPropDescs(b));
  };
  var __export = function __export(target, all) {
    for (var name in all) __defProp(target, name, {
      get: all[name],
      enumerable: true
    });
  };
  var __copyProps = function __copyProps(to, from, except, desc) {
    if (from && _typeof(from) === "object" || typeof from === "function") {
      var _iterator2 = _createForOfIteratorHelper(__getOwnPropNames(from)),
        _step2;
      try {
        var _loop = function _loop() {
          var key = _step2.value;
          if (!__hasOwnProp.call(to, key) && key !== except) __defProp(to, key, {
            get: function get() {
              return from[key];
            },
            enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable
          });
        };
        for (_iterator2.s(); !(_step2 = _iterator2.n()).done;) {
          _loop();
        }
      } catch (err) {
        _iterator2.e(err);
      } finally {
        _iterator2.f();
      }
    }
    return to;
  };
  var __toCommonJS = function __toCommonJS(mod) {
    return __copyProps(__defProp({}, "__esModule", {
      value: true
    }), mod);
  };
  var native_bridge_exports = {};
  __export(native_bridge_exports, {
    createNativeTurn: function createNativeTurn() {
      return _createNativeTurn;
    },
    handleNativeBodyJson: function handleNativeBodyJson() {
      return _handleNativeBodyJson;
    },
    handleNativeRetrievalJson: function handleNativeRetrievalJson() {
      return _handleNativeRetrievalJson;
    },
    handleNativeTurnJson: function handleNativeTurnJson() {
      return _handleNativeTurnJson;
    }
  });
  var WAKE_PREFIX_PATTERNS = [/^(?:hey\b[\s,;:!?.-]*nexus\b)/i, /^(?:ok\b[\s,;:!?.-]*nexus\b)/i, /^(?:nexus\b[\s,;:!?.-]*r[ée]veille(?:[\s'’-]*toi)\b)/i, /^(?:r[ée]veille(?:[\s'’-]*toi)\b[\s,;:!?.-]*nexus\b)/i, /^(?:nexus\b)/i];
  var FULL_NAME_WAKE_PREFIX_PATTERNS = [/^(?:hey\b[\s,;:!?.-]*nexus\b[\s,;:!?.-]*companion\b)/i, /^(?:ok\b[\s,;:!?.-]*nexus\b[\s,;:!?.-]*companion\b)/i, /^(?:nexus\b[\s,;:!?.-]*companion\b)/i];
  var COMMAND_AFTER_WAKE_PATTERN = /^(?:souviens(?:-| )toi|retiens|m[ée]morise|garde en m[ée]moire|lance|ouvre|quelle?|quelles?|comment|peux[- ]tu|fais|cherche|recherche|planifie|active|d[ée]sactive|avance|recule|tourne|stop|arr[êe]te|[ée]tat|roue|direction)\b/i;
  var MEMORY_PREFIXES = ["souviens toi", "souviens-toi", "retiens", "retiens ca", "memorise", "garde en memoire"];
  var normalizeWakePhrase = function normalizeWakePhrase(input) {
    return input.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^a-z0-9\s-]/g, " ").replace(/[-_]+/g, " ").replace(/\s+/g, " ").trim();
  };
  var matchWakePrefix = function matchWakePrefix(input) {
    return matchWakePrefixWithOptions(input);
  };
  var matchWakePrefixWithOptions = function matchWakePrefixWithOptions(input, options) {
    var raw = input.trimStart();
    if (!raw) return {
      matched: false,
      wakeOnly: false,
      command: "",
      matchedPrefix: ""
    };
    if (options == null ? void 0 : options.allowFullNameWake) {
      var fullNameMatch = raw.match(/^(?:hey\b[\s,;:!?.-]*|ok\b[\s,;:!?.-]*)?nexus\b[\s,;:!?.-]*companion\b/i);
      if (fullNameMatch && fullNameMatch.index === 0) {
        var afterFullName = raw.slice(fullNameMatch[0].length).replace(/^[\s,;:!?.-]+/, "");
        if (afterFullName.length > 0 && !COMMAND_AFTER_WAKE_PATTERN.test(afterFullName)) {
          return {
            matched: false,
            wakeOnly: false,
            command: raw,
            matchedPrefix: ""
          };
        }
      }
    }
    var patterns = (options == null ? void 0 : options.allowFullNameWake) ? [].concat(FULL_NAME_WAKE_PREFIX_PATTERNS, WAKE_PREFIX_PATTERNS) : WAKE_PREFIX_PATTERNS;
    var _iterator3 = _createForOfIteratorHelper(patterns),
      _step3;
    try {
      var _loop2 = function _loop2() {
          var pattern = _step3.value;
          var match = raw.match(pattern);
          if (!match || typeof match.index !== "number" || match.index !== 0) return 0;
          var matchedPrefix = match[0];
          var rest = raw.slice(matchedPrefix.length).replace(/^[\s,;:!?.-]+/, "");
          if (!(options == null ? void 0 : options.allowFullNameWake) && /^companion\b/i.test(rest)) return 0;
          if ((options == null ? void 0 : options.allowFullNameWake) && FULL_NAME_WAKE_PREFIX_PATTERNS.some(function (fullName) {
            return fullName.test(matchedPrefix);
          }) && rest.length > 0 && !COMMAND_AFTER_WAKE_PATTERN.test(rest)) {
            return 0;
          }
          return {
            v: {
              matched: true,
              wakeOnly: rest.length === 0,
              command: rest,
              matchedPrefix: matchedPrefix
            }
          };
        },
        _ret;
      for (_iterator3.s(); !(_step3 = _iterator3.n()).done;) {
        _ret = _loop2();
        if (_ret === 0) continue;
        if (_ret) return _ret.v;
      }
    } catch (err) {
      _iterator3.e(err);
    } finally {
      _iterator3.f();
    }
    return {
      matched: false,
      wakeOnly: false,
      command: raw,
      matchedPrefix: ""
    };
  };
  var isWakeOnlyInput = function isWakeOnlyInput(input) {
    return matchWakePrefix(input).wakeOnly;
  };
  var isWakeOnlyInputWithOptions = function isWakeOnlyInputWithOptions(input, options) {
    return matchWakePrefixWithOptions(input, options).wakeOnly;
  };
  var stripWakePrefix = function stripWakePrefix(input, options) {
    var match = matchWakePrefixWithOptions(input, options);
    if (!match.matched) return input.trim();
    return match.command;
  };
  var normalizeMemoryCommand = function normalizeMemoryCommand(input) {
    return normalizeWakePhrase(stripWakePrefix(input, {
      allowFullNameWake: true
    }).replace(/^(?:companion|nexus)\s+/i, ""));
  };
  var isIncompleteMemoryCommand = function isIncompleteMemoryCommand(input) {
    var normalized = normalizeMemoryCommand(input);
    if (!normalized) return false;
    if (MEMORY_PREFIXES.some(function (prefix) {
      return normalized === prefix;
    })) return true;
    if (normalized === "retiens ca" || normalized === "retiens cela") return true;
    if (normalized.startsWith("souviens toi") && !/\bque\b|\bde\b/.test(normalized)) return true;
    if (normalized.startsWith("retiens") && normalized.split(" ").length <= 2) return true;
    if (normalized.startsWith("memorise") && normalized.split(" ").length <= 2) return true;
    if (normalized.startsWith("garde en memoire") && normalized.split(" ").length <= 3) return true;
    return false;
  };
  var isWakeFragmentNoise = function isWakeFragmentNoise(input) {
    var normalized = normalizeWakePhrase(input);
    if (!normalized) return true;
    if (isWakeOnlyInput(input)) return true;
    if (isWakeOnlyInputWithOptions(input, {
      allowFullNameWake: true
    })) return true;
    if (isIncompleteMemoryCommand(input)) return true;
    if (/^nexus\s+comment$/.test(normalized)) return true;
    return false;
  };
  var unwrapPoliteRequest = function unwrapPoliteRequest(message) {
    return stripWakePrefix(message, {
      allowFullNameWake: true
    }).trim().replace(/^(?:(?:est[- ]ce que\s+)?(?:tu peux|vous pouvez|peux[- ]tu|pouvez[- ]vous|pourrais[- ]tu|pourriez[- ]vous)|je (?:voudrais|veux) savoir)\s+/i, "").replace(/^(?:me\s+|m['’])(?=(?:dire|expliquer|rappeler|apprendre|donner|parler)\b)/i, "").replace(/^(?:dire|expliquer|apprendre(?!\s+que\b))\s+/i, "");
  };
  var isExplicitMemoryRequest = function isExplicitMemoryRequest(message) {
    return /^(?:retiens|retenir|souviens[- ]toi|m[ée]morise|m[ée]moriser|garde en m[ée]moire|garder en m[ée]moire|apprends que|apprendre que|remember that)\b/i.test(unwrapPoliteRequest(message));
  };
  var personalFollowUps = new Map(Object.entries({
    "mon prenom": "Quel est mon prénom ?",
    "mon nom": "Quel est mon nom ?",
    "mon metier": "Quel est mon métier ?",
    "ma profession": "Quelle est ma profession ?",
    "mon age": "Quel est mon âge ?",
    "ma voiture": "Quelle est ma voiture ?",
    "ma couleur preferee": "Quelle est ma couleur préférée ?",
    "ma boisson preferee": "Quelle est ma boisson préférée ?"
  }));
  var analyzeQuestion = function analyzeQuestion(message) {
    var _a;
    var literal = stripWakePrefix(message, {
      allowFullNameWake: true
    }).trim();
    var followUp = (_a = normalizeWakePhrase(literal).match(/^et(?: pour)? (.+)$/)) == null ? void 0 : _a[1];
    var raw = followUp && personalFollowUps.get(followUp) || literal;
    var unwrapped = unwrapPoliteRequest(raw);
    var content = unwrapped.replace(/^(?:explique(?:[- ]moi)?\s+|dis[- ]moi\s+|parle[- ]moi de\s+|d[ée]cris(?:[- ]moi)?\s+)/i, "");
    var normalized = normalizeWakePhrase(content);
    var style = /^(?:parle[- ]moi de mani[èe]re|sois plus|garde un style)\b/i.test(raw);
    var question = !isExplicitMemoryRequest(raw) && !style && (raw.includes("?") || content !== raw || /^(?:quels?\b|quelles?\b|qu['’ ]est|c['’ ]est quoi\b|comment\b|pourquoi\b|qui\b|o[ùu](?=\s|$)|quand\b|est[- ]ce\b|explique\b|dis[- ]moi\b|parle[- ]moi\b|d[ée]cris\b|dans quelle\b)/i.test(raw));
    var kind = /^(?:ou\b|dans quelle (?:ville|entreprise|region)|a quel endroit)/.test(normalized) ? "where" : /^pourquoi\b/.test(normalized) ? "why" : /^quand\b|^a quelle (?:date|heure)\b/.test(normalized) ? "when" : "general";
    var subject = content.replace(/^(?:qu['’ ]est[- ]ce qu(?:e\s+|['’])|c['’ ]est quoi\s+|explique(?:[- ]moi)?\s+|dis[- ]moi\s+|parle[- ]moi de\s+|d[ée]cris(?:[- ]moi)?\s+)/i, "").replace(/[.!?]+$/, "").trim();
    return {
      question: question,
      content: content,
      normalized: normalized,
      kind: kind,
      subject: subject,
      personal: /\b(?:mon|ma|mes|notre|nos|je|moi|j|m)\b/.test(normalized)
    };
  };
  var SENSITIVE_PATTERNS = [/santé|maladie|diagnostic|médicament|therapy|health/i, /religion|croyance|foi/i, /politique|parti|vote/i, /adresse|localisation|gps|location/i, /carte bancaire|iban|compte|revenu|salaire|crypto wallet|password/i, /intime|sexual|orientation/i];
  var PREFERENCE_PATTERNS = [/je préfère|je prefere|j'aime|tu peux me parler|je veux fonctionner|le cr[ée]ateur pr[ée]f[èe]re/i];
  var PROJECT_PATTERNS = [/projet|roadmap|produit|feature|release|deadline|objectif|sans internet|offline|le projet actuel est/i];
  var RELATIONSHIP_PATTERNS = [/parle[- ]moi de mani[èe]re (?:plus )?(?:professionnelle?|directe?|claire?|structur[ée]e?)/i, /sois plus (?:professionnel(?:le)?|direct(?:e)?|clair(?:e)?|structur[ée]e?)/i, /garde un style (?:clair|structur[ée]|professionnel|direct)/i, /notre relation doit rester professionnelle/i];
  var STATIC_USER_PROFILE_PATTERNS = [/je m['’]appelle\s+/i, /mon nom est\s+/i, /mon pr[ée]nom est\s+/i, /le cr[ée]ateur est\s+/i, /j['’]habite\s+/i, /je vis [àa]\s+/i, /vous habitez [àa]\s+/i, /mon m[ée]tier est\s+/i, /je travaille comme\s+/i, /je travaille en tant que\s+/i];
  var TRANSIENT_JE_SUIS_PATTERNS = [/^stress[ée]?$/, /^fatigu[ée]?$/, /^content(?:e)?$/, /^malade$/, /^triste$/, /^en col[èe]re$/, /^occup[ée]?$/, /^en retard$/, /^perdu(?:e)?$/, /^inquiet(?:e)?$/, /^disponible$/, /^indisponible$/, /^dans le train$/, /^en train de\b/];
  var STABLE_ROLE_KEYWORDS = /(d[ée]veloppeur|developpeur|ing[ée]nieur|ingenieur|technicien|auto-entrepreneur|entrepreneur|fondateur|cofondateur|architecte|consultant|designer|chef de projet|support)/i;
  var PRECISE_LOCATION_PATTERNS = [/adresse exacte|exact address|coordonn[ée]es gps|latitude|longitude|localisation exacte/i];
  var CLOUD_EVERYTHING_PATTERNS = [/stocke (toutes|tout) mes donn[ée]es dans le cloud|store everything.*cloud/i];
  var CREATOR_CANONICAL_IDENTITY = "ingénieur Amine 0410";
  var MEMORY_WRAPPER_PREFIX = /^(?:(?:nexus(?:\s+companion)?|companion)\s+)?(?:souviens(?:-| )toi(?:\s+que)?|retiens(?:\s+que)?|m[ée]morise(?:\s+que)?|garde en m[ée]moire(?:\s+que)?)\s+/i;
  var SENSITIVE_LOCATION_PATTERNS = [/^(?:j['’]habite|je vis|vous habitez|vous vivez)\s+[àa]\s+/i, /^(?:habite|vit)\s+[àa]\s+/i, /localisation|adresse/i];
  var isStableJeSuisProfile = function isStableJeSuisProfile(content) {
    var match = content.match(/je suis\s+([^,.!?]+)/i);
    if (!match) return false;
    var complement = match[1].trim().toLowerCase();
    if (!complement) return false;
    if (TRANSIENT_JE_SUIS_PATTERNS.some(function (pattern) {
      return pattern.test(complement);
    })) return false;
    return STABLE_ROLE_KEYWORDS.test(complement);
  };
  var isStableUserProfileStatement = function isStableUserProfileStatement(content) {
    return STATIC_USER_PROFILE_PATTERNS.some(function (pattern) {
      return pattern.test(content);
    }) || isStableJeSuisProfile(content);
  };
  var isSensitiveMemoryContent = function isSensitiveMemoryContent(content) {
    return SENSITIVE_PATTERNS.some(function (pattern) {
      return pattern.test(content);
    });
  };
  var cleanMemoryFactText = function cleanMemoryFactText(content) {
    var unwrappedQuotes = content.trim().replace(/^["'`\xAB\xBB\u201C\u201D]+/, "").replace(/["'`\xAB\xBB\u201C\u201D]+$/, "").trim();
    var dedupedPunctuation = unwrappedQuotes.replace(/[!,\.:;\?]{2,}$/, function (value) {
      var _a;
      return (_a = value[0]) != null ? _a : "";
    }).replace(/["'`\xAB\xBB\u201C\u201D]+$/, "").trim();
    return dedupedPunctuation;
  };
  var canonicalizeCreatorIdentity = function canonicalizeCreatorIdentity(rawFact) {
    var _a, _b;
    var lower = rawFact.toLowerCase();
    if (!/(je m['’]appelle|mon nom est|mon pr[ée]nom est)/i.test(rawFact)) return null;
    if (/amine\s*0410/i.test(lower)) return "Le cr\xE9ateur est ".concat(CREATOR_CANONICAL_IDENTITY, ".");
    var named = (_b = (_a = rawFact.match(/(?:je m['’]appelle|mon nom est|mon pr[ée]nom est)\s+(.+)$/i)) == null ? void 0 : _a[1]) == null ? void 0 : _b.trim();
    if (!named) return null;
    return "Le cr\xE9ateur est ".concat(named, ".");
  };
  var normalizeMemoryFact = function normalizeMemoryFact(fact) {
    var _a, _b, _c, _d, _e, _f, _g, _h;
    var cleaned = cleanMemoryFactText(fact).replace(/\s+/g, " ").trim();
    if (!cleaned) return "";
    var canonicalIdentity = canonicalizeCreatorIdentity(cleaned);
    if (canonicalIdentity) return canonicalIdentity;
    var project = (_b = (_a = cleaned.match(/^mon projet actuel est\s+(.+)$/i)) == null ? void 0 : _a[1]) == null ? void 0 : _b.trim();
    if (project) return "Le projet actuel est ".concat(cleanMemoryFactText(project), ".");
    var looseProject = (_d = (_c = cleaned.match(/^(.+)\s+est mon projet[.!?]?$/i)) == null ? void 0 : _c[1]) == null ? void 0 : _d.trim();
    if (looseProject) return "Le projet actuel est ".concat(cleanMemoryFactText(looseProject), ".");
    var creatorPref = (_f = (_e = cleaned.match(/^je pr[ée]f[èe]re\s+(.+)$/i)) == null ? void 0 : _e[1]) == null ? void 0 : _f.trim();
    if (creatorPref) return "Le cr\xE9ateur pr\xE9f\xE8re ".concat(cleanMemoryFactText(creatorPref), ".");
    var liveAt = (_h = (_g = cleaned.match(/^j['’]habite\s+[àa]\s+(.+)$/i)) == null ? void 0 : _g[1]) == null ? void 0 : _h.trim();
    if (liveAt) return "Vous habitez \xE0 ".concat(cleanMemoryFactText(liveAt), ".");
    if (/[!\.\?]$/.test(cleaned)) return cleaned;
    return "".concat(cleaned, ".");
  };
  var stripMemoryCommandWrapper = function stripMemoryCommandWrapper(input) {
    var wakeStripped = unwrapPoliteRequest(input);
    return wakeStripped.replace(MEMORY_WRAPPER_PREFIX, "").replace(/^(?:retenir|m[ée]moriser|garder en m[ée]moire|apprends|apprendre)(?:\s+que)?\s+/i, "").trim();
  };
  var normalizeMemoryCandidateContent = function normalizeMemoryCandidateContent(input) {
    var stripped = stripMemoryCommandWrapper(input);
    return normalizeMemoryFact(stripped || input);
  };
  var normalizeMemoryContentKey = function normalizeMemoryContentKey(content) {
    return content.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/(?:[\0-\x08\x0E-\x1F!-\/:-@\[-`\{-\x9F\xA1-\xA9\xAB-\xB1\xB4\xB6-\xB8\xBB\xBF\xD7\xF7\u02C2-\u02C5\u02D2-\u02DF\u02E5-\u02EB\u02ED\u02EF-\u036F\u0375\u0378\u0379\u037E\u0380-\u0385\u0387\u038B\u038D\u03A2\u03F6\u0482-\u0489\u0530\u0557\u055A-\u055F\u0589\u058A\u058D-\u05CF\u05EB-\u05EE\u05F3-\u061F\u064B-\u065F\u066A-\u066D\u0670\u06D4\u06D6-\u06E4\u06E7-\u06ED\u06FD\u06FE\u0700-\u070F\u0711\u0730-\u074C\u07A6-\u07B0\u07B2-\u07BF\u07EB-\u07F3\u07F6-\u07F9\u07FB-\u07FF\u0816-\u0819\u081B-\u0823\u0825-\u0827\u0829-\u083F\u0859-\u085F\u086B-\u086F\u0888\u0890-\u089F\u08CA-\u0903\u093A-\u093C\u093E-\u094F\u0951-\u0957\u0962-\u0965\u0970\u0981-\u0984\u098D\u098E\u0991\u0992\u09A9\u09B1\u09B3-\u09B5\u09BA-\u09BC\u09BE-\u09CD\u09CF-\u09DB\u09DE\u09E2-\u09E5\u09F2\u09F3\u09FA\u09FB\u09FD-\u0A04\u0A0B-\u0A0E\u0A11\u0A12\u0A29\u0A31\u0A34\u0A37\u0A3A-\u0A58\u0A5D\u0A5F-\u0A65\u0A70\u0A71\u0A75-\u0A84\u0A8E\u0A92\u0AA9\u0AB1\u0AB4\u0ABA-\u0ABC\u0ABE-\u0ACF\u0AD1-\u0ADF\u0AE2-\u0AE5\u0AF0-\u0AF8\u0AFA-\u0B04\u0B0D\u0B0E\u0B11\u0B12\u0B29\u0B31\u0B34\u0B3A-\u0B3C\u0B3E-\u0B5B\u0B5E\u0B62-\u0B65\u0B70\u0B78-\u0B82\u0B84\u0B8B-\u0B8D\u0B91\u0B96-\u0B98\u0B9B\u0B9D\u0BA0-\u0BA2\u0BA5-\u0BA7\u0BAB-\u0BAD\u0BBA-\u0BCF\u0BD1-\u0BE5\u0BF3-\u0C04\u0C0D\u0C11\u0C29\u0C3A-\u0C3C\u0C3E-\u0C57\u0C5B\u0C5E\u0C5F\u0C62-\u0C65\u0C70-\u0C77\u0C7F\u0C81-\u0C84\u0C8D\u0C91\u0CA9\u0CB4\u0CBA-\u0CBC\u0CBE-\u0CDB\u0CDF\u0CE2-\u0CE5\u0CF0\u0CF3-\u0D03\u0D0D\u0D11\u0D3B\u0D3C\u0D3E-\u0D4D\u0D4F-\u0D53\u0D57\u0D62-\u0D65\u0D79\u0D80-\u0D84\u0D97-\u0D99\u0DB2\u0DBC\u0DBE\u0DBF\u0DC7-\u0DE5\u0DF0-\u0E00\u0E31\u0E34-\u0E3F\u0E47-\u0E4F\u0E5A-\u0E80\u0E83\u0E85\u0E8B\u0EA4\u0EA6\u0EB1\u0EB4-\u0EBC\u0EBE\u0EBF\u0EC5\u0EC7-\u0ECF\u0EDA\u0EDB\u0EE0-\u0EFF\u0F01-\u0F1F\u0F34-\u0F3F\u0F48\u0F6D-\u0F87\u0F8D-\u0FFF\u102B-\u103E\u104A-\u104F\u1056-\u1059\u105E-\u1060\u1062-\u1064\u1067-\u106D\u1071-\u1074\u1082-\u108D\u108F\u109A-\u109F\u10C6\u10C8-\u10CC\u10CE\u10CF\u10FB\u1249\u124E\u124F\u1257\u1259\u125E\u125F\u1289\u128E\u128F\u12B1\u12B6\u12B7\u12BF\u12C1\u12C6\u12C7\u12D7\u1311\u1316\u1317\u135B-\u1368\u137D-\u137F\u1390-\u139F\u13F6\u13F7\u13FE-\u1400\u166D\u166E\u169B-\u169F\u16EB-\u16ED\u16F9-\u16FF\u1712-\u171E\u1732-\u173F\u1752-\u175F\u176D\u1771-\u177F\u17B4-\u17D6\u17D8-\u17DB\u17DD-\u17DF\u17EA-\u17EF\u17FA-\u180F\u181A-\u181F\u1879-\u187F\u1885\u1886\u18A9\u18AB-\u18AF\u18F6-\u18FF\u191F-\u1945\u196E\u196F\u1975-\u197F\u19AC-\u19AF\u19CA-\u19CF\u19DB-\u19FF\u1A17-\u1A1F\u1A55-\u1A7F\u1A8A-\u1A8F\u1A9A-\u1AA6\u1AA8-\u1B04\u1B34-\u1B44\u1B4D-\u1B4F\u1B5A-\u1B82\u1BA1-\u1BAD\u1BE6-\u1BFF\u1C24-\u1C3F\u1C4A-\u1C4C\u1C7E\u1C7F\u1C8B-\u1C8F\u1CBB\u1CBC\u1CC0-\u1CE8\u1CED\u1CF4\u1CF7-\u1CF9\u1CFB-\u1CFF\u1DC0-\u1DFF\u1F16\u1F17\u1F1E\u1F1F\u1F46\u1F47\u1F4E\u1F4F\u1F58\u1F5A\u1F5C\u1F5E\u1F7E\u1F7F\u1FB5\u1FBD\u1FBF-\u1FC1\u1FC5\u1FCD-\u1FCF\u1FD4\u1FD5\u1FDC-\u1FDF\u1FED-\u1FF1\u1FF5\u1FFD-\u1FFF\u200B-\u2027\u202A-\u202E\u2030-\u205E\u2060-\u206F\u2072\u2073\u207A-\u207E\u208A-\u208E\u20A0-\u2101\u2103-\u2106\u2108\u2109\u2114\u2116-\u2118\u211E-\u2123\u2125\u2127\u2129\u212E\u213A\u213B\u2140-\u2144\u214A-\u214D\u214F\u218A-\u245F\u249C-\u24E9\u2500-\u2775\u2794-\u2BFF\u2CE5-\u2CEA\u2CEF-\u2CF1\u2CF4-\u2CFC\u2CFE\u2CFF\u2D26\u2D28-\u2D2C\u2D2E\u2D2F\u2D68-\u2D6E\u2D70-\u2D7F\u2D97-\u2D9F\u2DA7\u2DAF\u2DB7\u2DBF\u2DC7\u2DCF\u2DD7\u2DDF-\u2E2E\u2E30-\u2FFF\u3001-\u3004\u3008-\u3020\u302A-\u3030\u3036\u3037\u303D-\u3040\u3097-\u309C\u30A0\u30FB\u3100-\u3104\u3130\u318F-\u3191\u3196-\u319F\u31C0-\u31EF\u3200-\u321F\u322A-\u3247\u3250\u3260-\u327F\u328A-\u32B0\u32C0-\u33FF\u4DC0-\u4DFF\uA48D-\uA4CF\uA4FE\uA4FF\uA60D-\uA60F\uA62C-\uA63F\uA66F-\uA67E\uA69E\uA69F\uA6F0-\uA716\uA720\uA721\uA789\uA78A\uA7DE-\uA7E1\uA7E3-\uA7F0\uA802\uA806\uA80B\uA823-\uA82F\uA836-\uA83F\uA874-\uA881\uA8B4-\uA8CF\uA8DA-\uA8F1\uA8F8-\uA8FA\uA8FC\uA8FF\uA926-\uA92F\uA947-\uA95F\uA97D-\uA983\uA9B3-\uA9CE\uA9DA-\uA9DF\uA9E5\uA9FF\uAA29-\uAA3F\uAA43\uAA4C-\uAA4F\uAA5A-\uAA5F\uAA77-\uAA79\uAA7B-\uAA7D\uAAB0\uAAB2-\uAAB4\uAAB7\uAAB8\uAABE\uAABF\uAAC1\uAAC3-\uAADA\uAADE\uAADF\uAAEB-\uAAF1\uAAF5-\uAB00\uAB07\uAB08\uAB0F\uAB10\uAB17-\uAB1F\uAB27\uAB2F\uAB5B\uAB6A\uAB6B\uAB6E\uAB6F\uABE3-\uABEF\uABFA-\uABFF\uD7A4-\uD7AF\uD7C7-\uD7CA\uD7FC-\uD7FF\uE000-\uF8FF\uFA6E\uFA6F\uFADA-\uFAFF\uFB07-\uFB12\uFB18-\uFB1C\uFB1E\uFB29\uFB37\uFB3D\uFB3F\uFB42\uFB45\uFBB2-\uFBD2\uFD3E-\uFD4F\uFD90\uFD91\uFDC8-\uFDEF\uFDFC-\uFE6F\uFE75\uFEFD\uFEFE\uFF00-\uFF0F\uFF1A-\uFF20\uFF3B-\uFF40\uFF5B-\uFF65\uFFBF-\uFFC1\uFFC8\uFFC9\uFFD0\uFFD1\uFFD8\uFFD9\uFFDD-\uFFFF]|\uD800[\uDC0C\uDC27\uDC3B\uDC3E\uDC4E\uDC4F\uDC5E-\uDC7F\uDCFB-\uDD06\uDD34-\uDD3F\uDD79-\uDD89\uDD8C-\uDE7F\uDE9D-\uDE9F\uDED1-\uDEE0\uDEFC-\uDEFF\uDF24-\uDF2C\uDF4B-\uDF4F\uDF76-\uDF7F\uDF9E\uDF9F\uDFC4-\uDFC7\uDFD0\uDFD6-\uDFFF]|\uD801[\uDC9E\uDC9F\uDCAA-\uDCAF\uDCD4-\uDCD7\uDCFC-\uDCFF\uDD28-\uDD2F\uDD64-\uDD6F\uDD7B\uDD8B\uDD93\uDD96\uDDA2\uDDB2\uDDBA\uDDBD-\uDDBF\uDDF4-\uDDFF\uDF37-\uDF3F\uDF56-\uDF5F\uDF68-\uDF7F\uDF86\uDFB1\uDFC0-\uDFFF]|\uD802[\uDC06\uDC07\uDC09\uDC36\uDC39-\uDC3B\uDC3D\uDC3E\uDC56\uDC57\uDC77\uDC78\uDC9F-\uDCA6\uDCB0-\uDCDF\uDCF3\uDCF6-\uDCFA\uDD1C-\uDD1F\uDD3A-\uDD3F\uDD5A-\uDD7F\uDDB8-\uDDBB\uDDD0\uDDD1\uDE01-\uDE0F\uDE14\uDE18\uDE36-\uDE3F\uDE49-\uDE5F\uDE7F\uDEA0-\uDEBF\uDEC8\uDEE5-\uDEEA\uDEF0-\uDEFF\uDF36-\uDF3F\uDF56\uDF57\uDF73-\uDF77\uDF92-\uDFA8\uDFB0-\uDFFF]|\uD803[\uDC49-\uDC7F\uDCB3-\uDCBF\uDCF3-\uDCF9\uDD24-\uDD2F\uDD3A-\uDD3F\uDD66-\uDD6E\uDD86-\uDE5F\uDE7F\uDEAA-\uDEAF\uDEB2-\uDEC1\uDEC8-\uDED8\uDEEF-\uDEFF\uDF28-\uDF2F\uDF46-\uDF50\uDF55-\uDF6F\uDF82-\uDFAF\uDFCC-\uDFDF\uDFF7-\uDFFF]|\uD804[\uDC00-\uDC02\uDC38-\uDC51\uDC70\uDC73\uDC74\uDC76-\uDC82\uDCB0-\uDCCF\uDCE9-\uDCEF\uDCFA-\uDD02\uDD27-\uDD35\uDD40-\uDD43\uDD45\uDD46\uDD48-\uDD4F\uDD73-\uDD75\uDD77-\uDD82\uDDB3-\uDDC0\uDDC5-\uDDCF\uDDDB\uDDDD-\uDDE0\uDDF5-\uDDFF\uDE12\uDE2C-\uDE3E\uDE41-\uDE7F\uDE87\uDE89\uDE8E\uDE9E\uDEA9-\uDEAF\uDEDF-\uDEEF\uDEFA-\uDF04\uDF0D\uDF0E\uDF11\uDF12\uDF29\uDF31\uDF34\uDF3A-\uDF3C\uDF3E-\uDF4F\uDF51-\uDF5C\uDF62-\uDF7F\uDF8A\uDF8C\uDF8D\uDF8F\uDFB6\uDFB8-\uDFD0\uDFD2\uDFD4-\uDFFF]|\uD805[\uDC35-\uDC46\uDC4B-\uDC4F\uDC5A-\uDC5E\uDC62-\uDC7F\uDCB0-\uDCC3\uDCC6\uDCC8-\uDCCF\uDCDA-\uDD7F\uDDAF-\uDDD7\uDDDC-\uDDFF\uDE30-\uDE43\uDE45-\uDE4F\uDE5A-\uDE7F\uDEAB-\uDEB7\uDEB9-\uDEBF\uDECA-\uDECF\uDEE4-\uDEFF\uDF1B-\uDF2F\uDF3C-\uDF3F\uDF47-\uDFFF]|\uD806[\uDC2C-\uDC9F\uDCF3-\uDCFE\uDD07\uDD08\uDD0A\uDD0B\uDD14\uDD17\uDD30-\uDD3E\uDD40\uDD42-\uDD4F\uDD5A-\uDD9F\uDDA8\uDDA9\uDDD1-\uDDE0\uDDE2\uDDE4-\uDDFF\uDE01-\uDE0A\uDE33-\uDE39\uDE3B-\uDE4F\uDE51-\uDE5B\uDE8A-\uDE9C\uDE9E-\uDEAF\uDEF9-\uDF09\uDF0B-\uDFBF\uDFE1-\uDFEF\uDFFA-\uDFFF]|\uD807[\uDC09\uDC2F-\uDC3F\uDC41-\uDC4F\uDC6D-\uDC71\uDC90-\uDCFF\uDD07\uDD0A\uDD31-\uDD45\uDD47-\uDD4F\uDD5A-\uDD5F\uDD66\uDD69\uDD8A-\uDD97\uDD99-\uDD9F\uDDAA-\uDDAF\uDDDC-\uDDDF\uDDEA-\uDDF0\uDDF2-\uDEDF\uDEF3-\uDF01\uDF03\uDF11\uDF34-\uDF4F\uDF5A-\uDFAF\uDFB1-\uDFBF\uDFD5-\uDFFF]|\uD808[\uDF9A-\uDFFF]|\uD809[\uDC70-\uDC74\uDD44-\uDD4F\uDE87-\uDFFF]|[\uD80A\uD812-\uD817\uD819\uD825-\uD82A\uD82D\uD82E\uD830-\uD832\uD836\uD83D\uD83F\uD87C\uD87D\uD87F\uD88E-\uD8B3\uD8C0-\uDBFF][\uDC00-\uDFFF]|\uD80B[\uDC00-\uDF8F\uDFF1-\uDFFF]|\uD80D[\uDC30-\uDC40\uDC47-\uDC5F]|\uD810[\uDFFB-\uDFFF]|\uD811[\uDE47-\uDFFF]|\uD818[\uDC00-\uDCFF\uDD1E-\uDD2F\uDD3A-\uDFFF]|\uD81A[\uDE39-\uDE3F\uDE5F\uDE6A-\uDE6F\uDEBF\uDECA-\uDECF\uDEEE-\uDEFF\uDF30-\uDF3F\uDF44-\uDF4F\uDF5A\uDF62\uDF78-\uDF7C\uDF90-\uDFFF]|\uD81B[\uDC00-\uDD3F\uDD6D-\uDD6F\uDD7A-\uDE3F\uDE97-\uDE9F\uDEB9\uDEBA\uDED4-\uDEFF\uDF4B-\uDF4F\uDF51-\uDF92\uDFA0-\uDFDF\uDFE2\uDFE4-\uDFF1\uDFF7-\uDFFF]|\uD823[\uDCDB-\uDCFE\uDD21-\uDD7F\uDDF3-\uDDFF]|\uD824[\uDD92-\uDD9F\uDDD3-\uDFFF]|\uD82B[\uDC00-\uDFEF\uDFF4\uDFFC\uDFFF]|\uD82C[\uDD29-\uDD31\uDD33-\uDD4F\uDD53\uDD54\uDD56-\uDD63\uDD69-\uDD6F\uDEFC-\uDFFF]|\uD82F[\uDC6B-\uDC6F\uDC7D-\uDC7F\uDC89-\uDC8F\uDC9A-\uDFFF]|\uD833[\uDC00-\uDCEF\uDCFA-\uDFFF]|\uD834[\uDC00-\uDEBF\uDED4-\uDEDF\uDEF4-\uDF5F\uDF79-\uDFFF]|\uD835[\uDC55\uDC9D\uDCA0\uDCA1\uDCA3\uDCA4\uDCA7\uDCA8\uDCAD\uDCBA\uDCBC\uDCC4\uDD06\uDD0B\uDD0C\uDD15\uDD1D\uDD3A\uDD3F\uDD45\uDD47-\uDD49\uDD51\uDEA7\uDEC1\uDEDB\uDEFB\uDF15\uDF35\uDF4F\uDF6F\uDF89\uDFA9\uDFC3\uDFCC\uDFCD]|\uD837[\uDC00-\uDEFF\uDF82-\uDF8F\uDF97-\uDFCC]|\uD838[\uDC00-\uDC2F\uDC6E-\uDCFF\uDD2D-\uDD36\uDD3E\uDD3F\uDD4A-\uDD4D\uDD4F-\uDE8F\uDEAE-\uDEBF\uDEEC-\uDEEF\uDEFA-\uDFFF]|\uD839[\uDC00-\uDCCF\uDCEC-\uDCEF\uDCFA-\uDDCF\uDDEE\uDDEF\uDDFB-\uDEBF\uDEDF\uDEE3\uDEE6\uDEEE\uDEEF\uDEF5-\uDEFD\uDF00-\uDFDF\uDFE7\uDFEC\uDFEF\uDFFF]|\uD83A[\uDCC5\uDCC6\uDCD0-\uDCFF\uDD44-\uDD4A\uDD4C-\uDD4F\uDD5A-\uDFFF]|\uD83B[\uDC00-\uDC70\uDCAC\uDCB0\uDCB5-\uDD00\uDD2E\uDD3E-\uDDFF\uDE04\uDE20\uDE23\uDE25\uDE26\uDE28\uDE33\uDE38\uDE3A\uDE3C-\uDE41\uDE43-\uDE46\uDE48\uDE4A\uDE4C\uDE50\uDE53\uDE55\uDE56\uDE58\uDE5A\uDE5C\uDE5E\uDE60\uDE63\uDE65\uDE66\uDE6B\uDE73\uDE78\uDE7D\uDE7F\uDE8A\uDE9C-\uDEA0\uDEA4\uDEAA\uDEBC-\uDFFF]|\uD83C[\uDC00-\uDCFF\uDD0D-\uDFFF]|\uD83E[\uDC00-\uDFEF\uDFFA-\uDFFF]|\uD869[\uDEE0-\uDEFF]|\uD86E\uDC1F|\uD873[\uDEAE\uDEAF]|\uD87A[\uDFE1-\uDFEF]|\uD87B[\uDE5E-\uDFFF]|\uD87E[\uDE1E-\uDFFF]|\uD884[\uDF4B-\uDF4F]|\uD88D[\uDC7A-\uDFFF]|\uD8BF[\uDC40-\uDFFF]|[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?:[^\uD800-\uDBFF]|^)[\uDC00-\uDFFF])/g, " ").replace(/\s+/g, " ").trim();
  };
  var shouldRequireLocationConfirmation = function shouldRequireLocationConfirmation(content) {
    return SENSITIVE_LOCATION_PATTERNS.some(function (pattern) {
      return pattern.test(content);
    });
  };
  var createMemoryItem = function createMemoryItem(memory) {
    var _a, _b, _c;
    if (!memory.type || !memory.content || !memory.source) {
      throw new Error("Memory item requires type, content and source.");
    }
    var now = Date.now();
    return __spreadProps(__spreadValues({}, memory), {
      id: (_a = memory.id) != null ? _a : "mem-".concat(now, "-").concat(Math.random().toString(36).slice(2, 7)),
      createdAt: (_b = memory.createdAt) != null ? _b : now,
      updatedAt: (_c = memory.updatedAt) != null ? _c : now
    });
  };
  var inferMemoryType = function inferMemoryType(content) {
    if (PROJECT_PATTERNS.some(function (pattern) {
      return pattern.test(content);
    })) return "project_context";
    if (isStableUserProfileStatement(content)) return "user_profile";
    if (PREFERENCE_PATTERNS.some(function (pattern) {
      return pattern.test(content);
    })) return "user_preference";
    if (RELATIONSHIP_PATTERNS.some(function (pattern) {
      return pattern.test(content);
    })) return "relationship_context";
    return "conversation_summary";
  };
  var inferMemoryLayer = function inferMemoryLayer(content) {
    if (PROJECT_PATTERNS.some(function (pattern) {
      return pattern.test(content);
    })) return "project_context";
    if (PREFERENCE_PATTERNS.some(function (pattern) {
      return pattern.test(content);
    })) return "preference";
    if (RELATIONSHIP_PATTERNS.some(function (pattern) {
      return pattern.test(content);
    })) return "relationship_context";
    return "episodic";
  };
  var inferSensitivity = function inferSensitivity(content) {
    if (PRECISE_LOCATION_PATTERNS.some(function (pattern) {
      return pattern.test(content);
    })) return "critical";
    if (isSensitiveMemoryContent(content)) return "high";
    return "low";
  };
  var inferStoragePreference = function inferStoragePreference(content, sensitive) {
    if (sensitive && PRECISE_LOCATION_PATTERNS.some(function (pattern) {
      return pattern.test(content);
    })) return "cloud_restricted";
    if (sensitive) return "cloud_restricted";
    if (CLOUD_EVERYTHING_PATTERNS.some(function (pattern) {
      return pattern.test(content);
    })) return "cloud_allowed";
    return "local";
  };
  var extractMemoryCandidates = function extractMemoryCandidates(userMessage) {
    var source = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : "user_message";
    var raw = stripWakePrefix(userMessage, {
      allowFullNameWake: true
    }).trim();
    if (analyzeQuestion(raw).question) return [];
    var rawForDetection = stripMemoryCommandWrapper(raw);
    var content = normalizeMemoryCandidateContent(raw);
    if (!content || content.length < 12) return [];
    if (isWakeFragmentNoise(content) || isIncompleteMemoryCommand(content)) return [];
    var looksMemorable = isExplicitMemoryRequest(raw) || PREFERENCE_PATTERNS.some(function (pattern) {
      return pattern.test(rawForDetection) || pattern.test(content);
    }) || PROJECT_PATTERNS.some(function (pattern) {
      return pattern.test(rawForDetection) || pattern.test(content);
    }) || RELATIONSHIP_PATTERNS.some(function (pattern) {
      return pattern.test(rawForDetection) || pattern.test(content);
    }) || isStableUserProfileStatement(rawForDetection) || isStableUserProfileStatement(content) || /mon prénom|mon prenom|souviens-toi|remember my|retiens que|m[ée]morise que|garde en m[ée]moire que/i.test(rawForDetection) || PRECISE_LOCATION_PATTERNS.some(function (pattern) {
      return pattern.test(rawForDetection) || pattern.test(content);
    });
    var likelySmallTalk = /^(ok|merci|salut|hello|bonjour)[!. ]*$/i.test(content);
    if (!looksMemorable || likelySmallTalk) return [];
    var sensitive = isSensitiveMemoryContent(content) || shouldRequireLocationConfirmation(content);
    var storagePreference = inferStoragePreference(content, sensitive);
    var cloudRisk = CLOUD_EVERYTHING_PATTERNS.some(function (pattern) {
      return pattern.test(content);
    });
    var sensitivity = inferSensitivity(content);
    return [createMemoryItem({
      type: inferMemoryType(content),
      layer: inferMemoryLayer(content),
      content: content,
      source: source,
      confidence: sensitive ? 0.52 : 0.78,
      importance: sensitive ? 0.5 : 0.7,
      stability: /toujours|par d[ée]faut|habituellement|sans internet/i.test(content) ? 0.7 : 0.52,
      sensitivity: sensitivity,
      tags: sensitive ? ["sensitive", "candidate"] : cloudRisk ? ["candidate", "cloud-risk"] : ["candidate"],
      requiresConfirmation: sensitive || cloudRisk || sensitivity === "critical",
      sensitive: sensitive,
      storagePreference: storagePreference
    })];
  };
  var clamp01 = function clamp01(value) {
    return Math.max(0, Math.min(1, value));
  };
  var VOLATILE_SIGNAL_TYPES = new Set(["app_mode", "app_online_status", "app_voice_status", "app_presence_status", "app_companion_state", "app_runtime_status"]);
  var VOLATILE_SIGNAL_FIELDS = new Set(["id", "capturedAt", "timestamp", "turnId", "sessionId", "createdAt", "updatedAt"]);
  var isRecord = function isRecord(value) {
    return _typeof(value) === "object" && value !== null && !Array.isArray(value);
  };
  var _stableStringify = function stableStringify(value) {
    if (value === null) return "null";
    if (typeof value === "string") return JSON.stringify(value);
    if (typeof value === "number" || typeof value === "boolean") return String(value);
    if (Array.isArray(value)) return "[".concat(value.map(function (entry) {
      return _stableStringify(entry);
    }).join(","), "]");
    if (!isRecord(value)) return JSON.stringify(String(value));
    var entries = Object.keys(value).sort().map(function (key) {
      return "".concat(JSON.stringify(key), ":").concat(_stableStringify(value[key]));
    });
    return "{".concat(entries.join(","), "}");
  };
  var _sanitizeVolatileMetadata = function sanitizeVolatileMetadata(value) {
    if (Array.isArray(value)) return value.map(function (entry) {
      return _sanitizeVolatileMetadata(entry);
    });
    if (!isRecord(value)) return value;
    var clean = {};
    for (var _i2 = 0, _Object$entries = Object.entries(value); _i2 < _Object$entries.length; _i2++) {
      var _Object$entries$_i = _slicedToArray(_Object$entries[_i2], 2),
        key = _Object$entries$_i[0],
        raw = _Object$entries$_i[1];
      if (VOLATILE_SIGNAL_FIELDS.has(key) || key === "ttl") continue;
      clean[key] = _sanitizeVolatileMetadata(raw);
    }
    return clean;
  };
  var SEMANTIC_TEXT_FIELDS = ["label", "title", "content", "text", "message", "summary", "description"];
  var pickSemanticText = function pickSemanticText(value) {
    for (var _i3 = 0, _SEMANTIC_TEXT_FIELDS = SEMANTIC_TEXT_FIELDS; _i3 < _SEMANTIC_TEXT_FIELDS.length; _i3++) {
      var field = _SEMANTIC_TEXT_FIELDS[_i3];
      var candidate = value[field];
      if (typeof candidate === "string" && candidate.trim()) return candidate.trim();
    }
    return void 0;
  };
  var isRuntimeTelemetryPayload = function isRuntimeTelemetryPayload(value) {
    return Object.keys(value).some(function (key) {
      return VOLATILE_SIGNAL_FIELDS.has(key);
    }) || "storagePreference" in value || "consentRequired" in value || "sensitivity" in value;
  };
  var isVolatileTechnicalSignal = function isVolatileTechnicalSignal(value) {
    if (!isRecord(value)) return false;
    var source = typeof value.source === "string" ? value.source : "";
    var type = typeof value.type === "string" ? value.type : "";
    if (source === "app_state") return true;
    if (VOLATILE_SIGNAL_TYPES.has(type)) return true;
    return false;
  };
  var isTechnicalMemoryContent = function isTechnicalMemoryContent(content) {
    var normalized = content.trim();
    if (!normalized) return false;
    if (!normalized.startsWith("{") || !normalized.endsWith("}")) return false;
    try {
      var parsed = JSON.parse(normalized);
      if (!isRecord(parsed)) return false;
      if (!isVolatileTechnicalSignal(parsed)) return false;
      return isRuntimeTelemetryPayload(parsed);
    } catch (e) {
      return false;
    }
  };
  var normalizeLearningInput = function normalizeLearningInput(event) {
    if (typeof event.input === "string") return {
      text: event.input,
      skipDurableMemory: isTechnicalMemoryContent(event.input)
    };
    if (isVolatileTechnicalSignal(event.input)) return {
      text: "",
      skipDurableMemory: true
    };
    var sanitized = _sanitizeVolatileMetadata(event.input);
    if (isRecord(sanitized)) {
      var semantic = pickSemanticText(sanitized);
      if (semantic) return {
        text: semantic,
        skipDurableMemory: false
      };
      if (Object.keys(sanitized).length === 0) return {
        text: "",
        skipDurableMemory: false
      };
    }
    return {
      text: _stableStringify(sanitized).slice(0, 320),
      skipDurableMemory: false
    };
  };
  var defaultLayerForText = function defaultLayerForText(text) {
    if (/pr[ée]f[èe]re|prefe?r|toujours|habituellement|d'habitude/i.test(text)) return "preference";
    if (/projet|roadmap|release|objectif|direction/i.test(text)) return "project_context";
    if (/corrige|tu t'es tromp[ée]|rectification|correction/i.test(text)) return "system_learning";
    return "episodic";
  };
  var sensitivityFromText = function sensitivityFromText(text) {
    if (/adresse exacte|iban|password|mot de passe|coordonn[ée]es gps|latitude|longitude|num[ée]ro de carte/i.test(text)) return "critical";
    if (isSensitiveMemoryContent(text)) return "high";
    if (/pr[ée]f[èe]re|projet|roadmap|mode/i.test(text)) return "low";
    return "medium";
  };
  var createLearningEvent = function createLearningEvent(_ref) {
    var type = _ref.type,
      input = _ref.input,
      source = _ref.source,
      suggestedMemoryLayer = _ref.suggestedMemoryLayer,
      _ref$createdAt = _ref.createdAt,
      createdAt = _ref$createdAt === void 0 ? Date.now() : _ref$createdAt,
      _ref$confidence = _ref.confidence,
      confidence = _ref$confidence === void 0 ? 0.7 : _ref$confidence,
      _ref$importance = _ref.importance,
      importance = _ref$importance === void 0 ? 0.6 : _ref$importance,
      requiresConfirmation = _ref.requiresConfirmation,
      _ref$riskFlags = _ref.riskFlags,
      riskFlags = _ref$riskFlags === void 0 ? [] : _ref$riskFlags;
    return {
      id: "learn-".concat(createdAt, "-").concat(Math.random().toString(36).slice(2, 8)),
      type: type,
      input: input,
      source: source,
      createdAt: createdAt,
      confidence: clamp01(confidence),
      importance: clamp01(importance),
      suggestedMemoryLayer: suggestedMemoryLayer,
      requiresConfirmation: requiresConfirmation,
      riskFlags: _toConsumableArray(riskFlags)
    };
  };
  var scoreMemoryImportance = function scoreMemoryImportance(input) {
    var normalized = input.content.toLowerCase();
    var score = 0.45;
    if (/projet|roadmap|direction|objectif/.test(normalized)) score += 0.2;
    if (/corrige|correction|creator|ing[ée]nieur amine 0410|amine 0410/.test(normalized)) score += 0.35;
    if (/pr[ée]f[èe]re|toujours|sans internet|offline/.test(normalized)) score += 0.2;
    if (/salut|merci|ok|cool/.test(normalized) && normalized.length < 25) score -= 0.25;
    if (input.source === "creator_instruction") score += 0.2;
    if (input.eventType === "creator_instruction" || input.eventType === "user_correction") score += 0.15;
    return clamp01(score);
  };
  var scoreMemoryStability = function scoreMemoryStability(input) {
    var score = 0.35 + Math.min(input.occurrences, 5) * 0.12;
    if (/toujours|par d[ée]faut|default|long[- ]term/i.test(input.content.toLowerCase())) score += 0.12;
    if (input.contradictions) score -= Math.min(0.35, input.contradictions * 0.16);
    return clamp01(score);
  };
  var shouldRemember = function shouldRemember(candidate) {
    var _a, _b;
    var text = candidate.content.trim();
    if (text.length < 15) return false;
    if (/^((ok|merci|bonjour|hello|salut)[!. ]*)+$/i.test(text)) return false;
    if (((_a = candidate.importance) != null ? _a : 0) < 0.5 && ((_b = candidate.stability) != null ? _b : 0) < 0.45) return false;
    return true;
  };
  var evaluateLearningEvent = function evaluateLearningEvent(event) {
    var _a;
    var normalized = normalizeLearningInput(event);
    if (normalized.skipDurableMemory) {
      return {
        accepted: false,
        reason: "technical_signal_excluded"
      };
    }
    if (!normalized.text.trim()) return {
      accepted: false,
      reason: "low_value_or_ephemeral"
    };
    var text = normalized.text;
    var sensitivity = sensitivityFromText(text);
    var importance = clamp01(Math.max(event.importance, scoreMemoryImportance({
      content: text,
      source: event.source,
      eventType: event.type
    })));
    var stability = scoreMemoryStability({
      content: text,
      occurrences: event.type === "repeated_preference" ? 3 : 1
    });
    var storagePreference = sensitivity === "high" || sensitivity === "critical" ? "cloud_restricted" : /cloud/.test(text.toLowerCase()) ? "cloud_allowed" : "local";
    var candidate = createMemoryItem({
      type: event.suggestedMemoryLayer === "project_context" ? "project_context" : event.suggestedMemoryLayer === "relationship_context" ? "relationship_context" : event.suggestedMemoryLayer === "preference" ? "user_preference" : "conversation_summary",
      layer: event.suggestedMemoryLayer,
      content: text.slice(0, 320),
      source: (_a = event.source) != null ? _a : "user_message",
      confidence: clamp01(event.confidence),
      importance: importance,
      stability: stability,
      sensitivity: sensitivity,
      requiresConfirmation: event.requiresConfirmation || sensitivity === "high" || sensitivity === "critical",
      sensitive: sensitivity === "high" || sensitivity === "critical",
      storagePreference: storagePreference,
      tags: ["candidate", "layer:".concat(event.suggestedMemoryLayer), "event:".concat(event.type)],
      evidence: [{
        id: "evidence-".concat(event.id),
        type: event.type === "environment_signal" ? "signal" : event.type === "user_correction" ? "correction" : "interaction",
        source: candidateSourceFromEvent(event),
        detail: text.slice(0, 160),
        capturedAt: event.createdAt
      }]
    });
    if (!shouldRemember(candidate)) return {
      accepted: false,
      reason: "low_value_or_ephemeral"
    };
    if (event.riskFlags.includes("hidden_surveillance_request")) return {
      accepted: false,
      reason: "unsafe_request"
    };
    return {
      accepted: true,
      reason: "accepted",
      candidate: candidate
    };
  };
  var candidateSourceFromEvent = function candidateSourceFromEvent(event) {
    if (event.type === "creator_instruction") return "creator_instruction";
    if (event.type === "environment_signal") return "environment_signal";
    return "user_message";
  };
  var buildCognitiveMemorySummary = function buildCognitiveMemorySummary(memories) {
    var prioritized = _toConsumableArray(memories).sort(function (a, b) {
      return b.importance + b.confidence - (a.importance + a.confidence);
    });
    var takeContents = function takeContents(predicate, limit) {
      return prioritized.filter(function (memory) {
        return predicate(memory) && !(memory.sensitive || memory.sensitivity === "high" || memory.sensitivity === "critical");
      }).slice(0, limit).map(function (memory) {
        return memory.content;
      });
    };
    return {
      creatorUserContext: takeContents(function (memory) {
        return /amine 0410|ing[ée]nieur amine 0410|creator|utilisateur|user/i.test(memory.content);
      }, 4),
      currentProjects: takeContents(function (memory) {
        return memory.layer === "project_context" || memory.type === "project_context";
      }, 6),
      preferences: takeContents(function (memory) {
        return memory.layer === "preference" || memory.type === "user_preference";
      }, 6),
      habits: takeContents(function (memory) {
        return memory.layer === "habit";
      }, 4),
      relationshipContext: takeContents(function (memory) {
        return memory.layer === "relationship_context" || memory.type === "relationship_context";
      }, 4),
      safetyConstraints: takeContents(function (memory) {
        return /s[ée]curit[ée]|privacy|confidential|consent|approval/i.test(memory.content);
      }, 6),
      recentImportantEpisodic: prioritized.filter(function (memory) {
        var _a;
        return ((_a = memory.layer) != null ? _a : defaultLayerForText(memory.content)) === "episodic" && memory.importance >= 0.65;
      }).slice(0, 5).map(function (memory) {
        return memory.content;
      })
    };
  };
  var buildCompanionContext = function buildCompanionContext(_ref2) {
    var profile = _ref2.profile,
      userMessage = _ref2.userMessage,
      memories = _ref2.memories,
      recentConversationSummary = _ref2.recentConversationSummary,
      appState = _ref2.appState,
      voiceState = _ref2.voiceState,
      environmentSignals = _ref2.environmentSignals,
      recentLearningEvents = _ref2.recentLearningEvents,
      brainSummary = _ref2.brainSummary,
      _ref2$includeCognitiv = _ref2.includeCognitiveSummary,
      includeCognitiveSummary = _ref2$includeCognitiv === void 0 ? true : _ref2$includeCognitiv;
    return __spreadProps(__spreadValues({}, function () {
      var confirmedMemories = memories.filter(function (memory) {
        return memory.lifecycleState !== "pending_confirmation" && !memory.requiresConfirmation && !memory.sensitive && memory.sensitivity !== "high" && memory.sensitivity !== "critical";
      });
      return {
        relevantMemories: confirmedMemories.slice(0, 8),
        cognitiveMemorySummary: includeCognitiveSummary ? buildCognitiveMemorySummary(confirmedMemories) : void 0
      };
    }()), {
      profile: profile,
      userMessage: userMessage,
      environmentSignals: environmentSignals,
      recentLearningEvents: recentLearningEvents,
      brainSummary: brainSummary,
      recentConversationSummary: recentConversationSummary,
      appState: appState,
      voiceState: voiceState,
      createdAt: Date.now()
    });
  };
  var normalize = function normalize(text) {
    return text.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^a-z0-9]+/g, " ").trim();
  };
  var stop = new Set("a au aux ca ce ces de des du en et est il je la le les ma me mes mon ne nos notre on ou par pas pour que quel quelle quelles quels qui sa se ses son sur ta te tes toi ton tu un une vous votre vos comment pourquoi quand peux peut pourrais dire dis explique expliquer parle parler moi qu ai as fait ce c d l s t j n y propos sujet sais savoir encore note notes rappelle souviens".split(" "));
  var families = [["prenom", "nom", "appelle"], ["metier", "profession", "travaille", "travail", "emploi", "poste"], ["preference", "preferences", "prefere", "preferes", "preferee", "preferees", "aime", "aimes"], ["voiture", "vehicule", "automobile"], ["boisson", "the", "cafe", "jus"], ["couleur", "coloris"], ["age", "ans"], ["projet", "projets"], ["objectif", "objectifs"]];
  var vocabulary = new Map(families.flatMap(function (family) {
    return family.map(function (word) {
      return [word, family[0]];
    });
  }));
  var words = function words(text) {
    return normalize(text).split(" ").filter(function (word) {
      return word.length > 1 && !stop.has(word);
    });
  };
  var canonical = function canonical(word) {
    var _a;
    return (_a = vocabulary.get(word)) != null ? _a : word;
  };
  var keys = function keys(text) {
    var result = words(text).map(canonical);
    if (/^(?:le createur est|je m appelle|mon prenom est|mon nom est)\b/.test(normalize(text))) result.push("prenom");
    return Array.from(new Set(result));
  };
  var nativeRetrievalTerms = function nativeRetrievalTerms(message) {
    var terms = words(analyzeQuestion(message).content).slice(0, 12);
    var expanded = terms.flatMap(function (word) {
      var _a;
      return (_a = families.find(function (family) {
        return family.includes(word);
      })) != null ? _a : [word];
    });
    if (terms.some(function (word) {
      return families[0].includes(word);
    })) expanded.push("le createur est");
    return Array.from(new Set(expanded)).slice(0, 36);
  };
  var toSecondPerson = function toSecondPerson(text) {
    return text.replace(/^ma\s+/i, "Ta ").replace(/^mes\s+/i, "Tes ").replace(/^mon\s+(?!pr[ée]nom\b|nom\b|m[ée]tier\b)/i, "Ton ").replace(/^le cr[ée]ateur est\s+/i, "Tu es ").replace(/^le cr[ée]ateur pr[ée]f[èe]re\s+/i, "Tu préfères ").replace(/^je m['’]appelle\s+/i, "Tu t’appelles ").replace(/^mon pr[ée]nom est\s+/i, "Ton prénom est ").replace(/^mon nom est\s+/i, "Ton nom est ").replace(/^je pr[ée]f[èe]re\s+/i, "Tu préfères ").replace(/^j['’]aime\s+/i, "Tu aimes ").replace(/^je travaille\s+/i, "Tu travailles ").replace(/^mon m[ée]tier est\s+/i, "Ton métier est ");
  };
  var answerFromNotes = function answerFromNotes(question, notes) {
    var request = analyzeQuestion(question);
    var query = keys(request.content);
    if (!query.length) return null;
    var anchors = query.filter(function (key) {
      return key !== "preference";
    });
    var candidates = notes.flatMap(function (note) {
      var titleKeys = keys(note.title);
      var sentences = note.excerpt.replace(/([.!?])\s+/g, "$1\n").split(/\n+/).map(function (text) {
        return text.trim();
      }).filter(Boolean);
      var labeledValue = sentences.length === 1 && note.excerpt.length <= 160 && !/\b(?:je|tu|il|elle|nous|vous|ils|elles|on|est|sont|a|ai|as|ont|fait|font|travaille|prefere|avance|reste)\b/.test(normalize(note.excerpt));
      return sentences.map(function (sentence) {
        var normalized = normalize(sentence);
        var slotMatches = request.kind === "where" ? /\b(?:a|au|aux|chez|dans|situe|situee)\b/.test(normalized) : request.kind === "why" ? /\b(?:parce que|car|puisque|en raison|a cause|grace a|pour son|pour sa|pour ses)\b/.test(normalized) : request.kind === "when" ? /\b(?:lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche|janvier|fevrier|mars|avril|mai|juin|juillet|aout|septembre|octobre|novembre|decembre|depuis|demain|hier|[0-9]+)\b/.test(normalized) : true;
        var evidence = keys(sentence);
        var matches = query.filter(function (key) {
          return evidence.includes(key) || titleKeys.includes(key);
        });
        var anchored = !anchors.length || anchors.some(function (key) {
          return evidence.includes(key) || titleKeys.includes(key);
        });
        var sentenceAnchor = (anchors.length ? anchors : query).some(function (key) {
          return evidence.includes(key);
        }) || labeledValue;
        var relativeMatches = query.filter(function (key) {
          return ["fille", "fils", "enfant", "fiancee", "chien", "chat"].includes(key);
        }).every(function (key) {
          return evidence.includes(key) || titleKeys.includes(key);
        });
        var attributeMatches = query.filter(function (key) {
          return ["prenom", "metier", "couleur", "age", "boisson", "voiture", "objectif"].includes(key);
        }).every(function (key) {
          return evidence.includes(key) || labeledValue && titleKeys.includes(key);
        });
        var score = slotMatches && relativeMatches && attributeMatches && anchored && sentenceAnchor && matches.length / query.length >= 0.5 ? matches.length * 10 + matches.filter(function (key) {
          return evidence.includes(key);
        }).length : 0;
        return {
          note: note,
          sentence: labeledValue && !query.some(function (key) {
            return evidence.includes(key);
          }) ? "".concat(note.title, " : ").concat(sentence) : sentence,
          score: score
        };
      });
    }).filter(function (item) {
      return item.score > 0;
    }).sort(function (a, b) {
      return b.score - a.score || b.note.recordedAt - a.note.recordedAt;
    });
    if (!candidates.length) return null;
    var best = candidates[0];
    var personal = request.personal;
    var conflicting = personal && candidates.find(function (item) {
      return item.score === best.score && item.note.id !== best.note.id && normalize(item.sentence) !== normalize(best.sentence);
    });
    if (conflicting) return {
      text: "J\u2019ai deux informations dans tes notes : \xAB ".concat(best.sentence.slice(0, 200), " \xBB et \xAB ").concat(conflicting.sentence.slice(0, 200), " \xBB. Laquelle est \xE0 jour ?"),
      evidenceIds: [best.note.id, conflicting.note.id]
    };
    var excerpt = best.sentence.length > 480 ? "".concat(best.sentence.slice(0, 477), "\u2026") : best.sentence;
    var spoken = personal ? toSecondPerson(excerpt) : excerpt;
    var prefix = best.note.manuallyEdited ? best.note.source.startsWith("https://fr.wikipedia.org/") ? "D’après ta note modifiée, dont la source d’origine est Wikipédia" : "D’après ta note modifiée" : best.note.source.startsWith("https://fr.wikipedia.org/") ? "D’après une note issue de Wikipédia" : "D’après ce que tu m’as confié";
    return {
      text: "".concat(prefix, " : ").concat(spoken),
      evidenceIds: [best.note.id]
    };
  };
  var validIdentifier = function validIdentifier(value) {
    return typeof value === "string" && value.length > 0 && value.length <= 128 && /^[a-zA-Z0-9._:-]+$/.test(value);
  };
  function isBoundedMotionRequest(value) {
    if (!value || _typeof(value) !== "object") return false;
    var item = value;
    return validIdentifier(item.motionGroup) && validIdentifier(item.action) && typeof item.durationMs === "number" && Number.isSafeInteger(item.durationMs) && item.durationMs > 0 && item.durationMs <= 5e3 && typeof item.normalizedSpeed === "number" && Number.isFinite(item.normalizedSpeed) && item.normalizedSpeed >= 0 && item.normalizedSpeed <= 1;
  }
  var virtualTricycleProfile = function virtualTricycleProfile() {
    var steeringAxle = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : "front";
    return {
      steeringAxle: steeringAxle,
      wheelbaseM: 0.28,
      trackM: 0.26,
      wheelRadiusM: 0.05,
      maxSpeedMps: 0.15,
      maxSteeringRad: 0.7
    };
  };
  var planTricycleMotion = function planTricycleMotion(profile, linearMps, angularRadps, durationMs) {
    if (!["front", "rear"].includes(profile.steeringAxle) || ![profile.wheelbaseM, profile.trackM, profile.wheelRadiusM, profile.maxSpeedMps, profile.maxSteeringRad].every(function (n) {
      return Number.isFinite(n) && n > 0;
    }) || profile.maxSpeedMps > 0.3 || profile.maxSteeringRad >= Math.PI / 2 || !Number.isFinite(linearMps) || !Number.isFinite(angularRadps) || Math.abs(linearMps) > profile.maxSpeedMps || Math.abs(angularRadps) > 1 || linearMps === 0 && angularRadps !== 0) throw new Error("Invalid tricycle motion");
    var steeringRad = linearMps === 0 ? 0 : Math.atan(angularRadps * profile.wheelbaseM / linearMps) * (profile.steeringAxle === "front" ? 1 : -1);
    var request = {
      motionGroup: "wheeled-base",
      action: "roll",
      durationMs: durationMs,
      normalizedSpeed: Math.abs(linearMps) / profile.maxSpeedMps
    };
    if (Math.abs(steeringRad) > profile.maxSteeringRad || !isBoundedMotionRequest(request)) throw new Error("Motion exceeds tricycle limits");
    var sample = function sample(elapsedMs) {
      var t = elapsedMs / 1e3;
      var headingRad = angularRadps * t;
      return {
        elapsedMs: elapsedMs,
        xM: angularRadps === 0 ? linearMps * t : linearMps / angularRadps * Math.sin(headingRad),
        yM: angularRadps === 0 ? 0 : linearMps / angularRadps * (1 - Math.cos(headingRad)),
        headingRad: headingRad,
        distanceM: linearMps * t
      };
    };
    var samples = [];
    for (var elapsed = 0; elapsed < durationMs; elapsed += 100) samples.push(sample(elapsed));
    samples.push(sample(durationMs));
    return {
      protocolVersion: 1,
      execution: "simulation",
      steeringAxle: profile.steeringAxle,
      request: request,
      linearMps: linearMps,
      angularRadps: angularRadps,
      steeringRad: steeringRad,
      leftWheelRadps: (linearMps - angularRadps * profile.trackM / 2) / profile.wheelRadiusM,
      rightWheelRadps: (linearMps + angularRadps * profile.trackM / 2) / profile.wheelRadiusM,
      samples: samples
    };
  };
  var numbers = {
    un: 1,
    une: 1,
    deux: 2,
    trois: 3,
    quatre: 4,
    cinq: 5,
    dix: 10,
    quinze: 15,
    vingt: 20,
    trente: 30,
    quarante: 40,
    cinquante: 50,
    soixante: 60
  };
  var resolveBodyCommand = function resolveBodyCommand(command, axle) {
    var _a;
    var text = command.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[’']/g, " ").replace(/[!?.,;:]/g, " ").replace(/\s+/g, " ").trim();
    if (/^(?:stop|arrete|arrete[- ]toi|arret|stoppe|stoppe[- ]toi|arrete le corps|arret du corps)$/.test(text)) return {
      handled: true,
      action: "stop",
      text: "Corps virtuel arrêté."
    };
    if (/^(?:etat du corps|etat du robot|ou en est ton corps|combien de roues as tu|comment est ton corps|decris ton corps)$/.test(text)) return {
      handled: true,
      action: "status",
      text: "Mon corps virtuel roule sur trois roues. La roue directrice est \xE0 l\u2019".concat(axle === "front" ? "avant" : "arrière", ".")
    };
    var straight = text.match(/^(avance|avancer|recule|reculer)(?: de)?(?: ([0-9]+|un|une|deux|trois|quatre|cinq|dix|quinze|vingt|trente|quarante|cinquante) (cm|centimetres?|metres?))?$/);
    var turn = text.match(/^tourne(?: a)? (gauche|droite)(?: de ([0-9]+|dix|quinze|vingt|trente|quarante|cinquante|soixante) degres?)?$/);
    if (!straight && !turn) return {
      handled: false,
      text: ""
    };
    var quantity = function quantity(value, fallback) {
      var _a2;
      return value ? (_a2 = numbers[value]) != null ? _a2 : Number(value) : fallback;
    };
    if (straight) {
      var cm = quantity(straight[2], 15) * (((_a = straight[3]) == null ? void 0 : _a.startsWith("metre")) ? 100 : 1);
      if (cm < 1 || cm > 50) return {
        handled: true,
        text: "Pour le corps virtuel, demande une distance de 1 à 50 centimètres."
      };
      var direction = straight[1].startsWith("rec") ? -1 : 1;
      return {
        handled: true,
        action: "drive",
        text: "Simulation : je ".concat(direction === 1 ? "roule en avant" : "recule", " de ").concat(cm, " centim\xE8tres."),
        plan: planTricycleMotion(virtualTricycleProfile(axle), direction * 0.1, 0, Math.round(cm * 100))
      };
    }
    var degrees = quantity(turn == null ? void 0 : turn[2], 20);
    if (degrees < 1 || degrees > 60) return {
      handled: true,
      text: "Pour le corps virtuel, demande un virage de 1 à 60 degrés."
    };
    var left = (turn == null ? void 0 : turn[1]) === "gauche";
    return {
      handled: true,
      action: "drive",
      text: "Simulation : je roule en virant \xE0 ".concat(left ? "gauche" : "droite", " de ").concat(degrees, " degr\xE9s."),
      plan: planTricycleMotion(virtualTricycleProfile(axle), 0.1, left ? 0.25 : -0.25, Math.round(degrees * Math.PI / 180 / 0.25 * 1e3))
    };
  };
  var hasQuestion = function hasQuestion(text) {
    return text.includes("?");
  };
  var buildEnvironmentConsentPromptNeeded = function buildEnvironmentConsentPromptNeeded(text) {
    return /apprends de ton environnement|observe|monitor|surveille/i.test(text);
  };
  var containsHiddenSurveillanceRequest = function containsHiddenSurveillanceRequest(text) {
    return /observe-moi sans me le dire|surveillance cach[ée]e|hidden telemetry|espionne|spy on/i.test(text);
  };
  var containsSelfModificationRequest = function containsSelfModificationRequest(text) {
    return /modifi(?:er|e).*code|modifie.*propre code|self[- ]?modify|auto[- ]?commit|auto[- ]?patch|modifie ton code tout seul/i.test(text);
  };
  var containsDestructiveFormatRequest = function containsDestructiveFormatRequest(text) {
    var formatVerb = /format(e|er|age)?/i.test(text);
    var destructiveTarget = /disque|disk|stockage|storage|m[ée]moire|memory|donn[ée]es|data|app state|[ée]tat de l['’]app/i.test(text);
    var massOperation = /tout|toutes|all|entier|entière/i.test(text);
    return formatVerb && destructiveTarget && (massOperation || /disque|disk|stockage|storage/.test(text));
  };
  var containsDestructiveActionRequest = function containsDestructiveActionRequest(text) {
    return /supprime tout|efface (tout|toutes les donn[ée]es)|delete everything|wipe all|r[ée]initialise toute la m[ée]moire|vide toutes les m[ée]moires|delete user data|wipe app state/i.test(text) || containsDestructiveFormatRequest(text);
  };
  var containsDeploymentBypassRequest = function containsDeploymentBypassRequest(text) {
    return /met(?:s|tre)? en prod sans validation|d[ée]ploie sans validation|deploy without validation|bypass (la )?validation( du cr[ée]ateur)?/i.test(text);
  };
  var explicitLearnRequest = function explicitLearnRequest(text) {
    return /souviens-toi|retiens que|remember that|apprends/i.test(text);
  };
  var requestsSensitiveSensorActivation = function requestsSensitiveSensorActivation(text) {
    return /active.*(cam[ée]ra|micro|microphone|location|localisation|gps)|turn on.*(camera|microphone|location)|ouvre.*cam[ée]ra/i.test(text);
  };
  var buildSignalFromContext = function buildSignalFromContext(context) {
    var _a, _b, _c;
    var signals = [];
    if (typeof ((_a = context.appState) == null ? void 0 : _a.isOnline) === "boolean") {
      signals.push({
        id: "signal-online-".concat(context.createdAt),
        type: "app_online_status",
        value: context.appState.isOnline,
        source: "app_state",
        capturedAt: context.createdAt,
        sensitivity: "low",
        consentRequired: false,
        storagePreference: "local",
        ttl: 1e3 * 60 * 60
      });
    }
    if ((_b = context.appState) == null ? void 0 : _b.visualMode) {
      signals.push({
        id: "signal-mode-".concat(context.createdAt),
        type: "app_mode",
        value: context.appState.visualMode,
        source: "app_state",
        capturedAt: context.createdAt,
        sensitivity: "low",
        consentRequired: false,
        storagePreference: "local",
        ttl: 1e3 * 60 * 60
      });
    }
    if ((_c = context.voiceState) == null ? void 0 : _c.wakeState) {
      signals.push({
        id: "signal-voice-".concat(context.createdAt),
        type: "voice_state",
        value: context.voiceState.wakeState,
        source: "voice_state",
        capturedAt: context.createdAt,
        sensitivity: "low",
        consentRequired: false,
        storagePreference: "local",
        ttl: 1e3 * 60 * 30
      });
    }
    return signals;
  };
  var decideCompanionResponse = function decideCompanionResponse(context) {
    var strippedUserMessage = stripWakePrefix(context.userMessage, {
      allowFullNameWake: true
    });
    var text = strippedUserMessage.trim();
    var normalizedText = normalizeMemoryCandidateContent(text);
    var memoryCandidates = extractMemoryCandidates(text);
    var sensitive = isSensitiveMemoryContent(text) || isSensitiveMemoryContent(normalizedText) || shouldRequireLocationConfirmation(text) || shouldRequireLocationConfirmation(normalizedText);
    var lowerText = text.toLowerCase();
    var riskFlags = [];
    if (isWakeOnlyInputWithOptions(context.userMessage, {
      allowFullNameWake: true
    })) {
      return {
        intent: "ask_clarification",
        memoryCandidates: [],
        learningEvents: [],
        suggestedResponseStyle: "clear",
        requiredConfirmations: [],
        riskFlags: ["wake_only_input"],
        nextVisualState: "listening"
      };
    }
    if (isIncompleteMemoryCommand(context.userMessage)) {
      return {
        intent: "ask_clarification",
        memoryCandidates: [],
        learningEvents: [],
        suggestedResponseStyle: "clear",
        requiredConfirmations: [],
        riskFlags: ["incomplete_memory_command"],
        nextVisualState: "speaking"
      };
    }
    var requiresConfirmation = sensitive ? ["sensitive_memory_confirmation"] : [];
    var learningEvents = [];
    if (containsSelfModificationRequest(lowerText)) {
      riskFlags.push("self_modification_request");
      requiresConfirmation.push("creator_code_change_approval_required");
      learningEvents.push(createLearningEvent({
        type: "safety_warning",
        input: text,
        source: "user_message",
        confidence: 0.9,
        importance: 0.95,
        suggestedMemoryLayer: "system_learning",
        requiresConfirmation: true,
        riskFlags: ["creator_approval_workflow_required"]
      }));
    }
    if (containsDestructiveActionRequest(lowerText)) {
      riskFlags.push("destructive_action_request");
      requiresConfirmation.push("explicit_authorization_required");
    }
    if (containsDeploymentBypassRequest(lowerText)) {
      riskFlags.push("deployment_validation_bypass_request");
      requiresConfirmation.push("creator_approval_required");
    }
    if (/d[ée]sactive.*(s[ée]curit[ée]|safety)|ignore.*safety/i.test(lowerText)) {
      riskFlags.push("disable_safety_request");
      return {
        intent: "safety_refusal",
        memoryCandidates: [],
        learningEvents: learningEvents,
        suggestedResponseStyle: "protective",
        requiredConfirmations: [],
        riskFlags: riskFlags,
        nextVisualState: "speaking"
      };
    }
    if (/stocke (tout|toutes).*cloud|store everything.*cloud/i.test(lowerText)) {
      riskFlags.push("cloud_overcollection_request");
      requiresConfirmation.push("cloud_minimization_warning");
    }
    if (containsHiddenSurveillanceRequest(lowerText)) {
      riskFlags.push("hidden_surveillance_request");
      return {
        intent: "safety_refusal",
        memoryCandidates: [],
        learningEvents: learningEvents,
        suggestedResponseStyle: "protective",
        requiredConfirmations: [],
        riskFlags: riskFlags,
        nextVisualState: "speaking"
      };
    }
    if (buildEnvironmentConsentPromptNeeded(lowerText)) {
      riskFlags.push("environment_monitoring_scope_required");
      requiresConfirmation.push("explicit_environment_scope_and_consent_required");
    }
    if (requestsSensitiveSensorActivation(lowerText)) {
      riskFlags.push("sensor_activation_consent_required");
      requiresConfirmation.push("explicit_environment_scope_and_consent_required");
    }
    if (/adresse exacte|coordonn[ée]es gps|latitude|longitude/i.test(lowerText)) {
      riskFlags.push("precise_location_sensitive");
      requiresConfirmation.push("sensitive_memory_confirmation");
      requiresConfirmation.push("local_only_storage_recommended");
    }
    if (/corrige|tu t'es tromp[ée]|non, c'est/i.test(lowerText)) {
      learningEvents.push(createLearningEvent({
        type: "user_correction",
        input: text,
        source: "user_message",
        confidence: 0.86,
        importance: 0.82,
        suggestedMemoryLayer: "system_learning",
        requiresConfirmation: false,
        riskFlags: []
      }));
    }
    if (/ing[ée]nieur amine 0410|amine 0410/i.test(lowerText) || /instruction cr[ée]ateur|creator instruction/i.test(lowerText)) {
      learningEvents.push(createLearningEvent({
        type: "creator_instruction",
        input: text,
        source: "creator_instruction",
        confidence: 0.95,
        importance: 0.96,
        suggestedMemoryLayer: /projet|direction|roadmap/i.test(lowerText) ? "project_context" : "system_learning",
        requiresConfirmation: false,
        riskFlags: []
      }));
    }
    if (/projet|roadmap|direction|objectif/i.test(lowerText) && explicitLearnRequest(lowerText)) {
      learningEvents.push(createLearningEvent({
        type: "project_update",
        input: text,
        source: "user_message",
        confidence: 0.8,
        importance: 0.84,
        suggestedMemoryLayer: "project_context",
        requiresConfirmation: false,
        riskFlags: []
      }));
    }
    var _iterator4 = _createForOfIteratorHelper(buildSignalFromContext(context)),
      _step4;
    try {
      for (_iterator4.s(); !(_step4 = _iterator4.n()).done;) {
        var signal = _step4.value;
        learningEvents.push(createLearningEvent({
          type: "environment_signal",
          input: signal,
          source: signal.source,
          confidence: 0.65,
          importance: 0.4,
          suggestedMemoryLayer: "environment_context",
          requiresConfirmation: false,
          riskFlags: []
        }));
      }
    } catch (err) {
      _iterator4.e(err);
    } finally {
      _iterator4.f();
    }
    for (var _i4 = 0, _learningEvents = learningEvents; _i4 < _learningEvents.length; _i4++) {
      var event = _learningEvents[_i4];
      var evaluated = evaluateLearningEvent(event);
      if (evaluated.accepted && evaluated.candidate) {
        memoryCandidates.push(evaluated.candidate);
      }
    }
    var existingKeys = new Set(context.relevantMemories.map(function (memory) {
      return normalizeMemoryContentKey(memory.content);
    }));
    var dedupedCandidates = memoryCandidates.filter(function (candidate, index, all) {
      candidate.content = normalizeMemoryCandidateContent(candidate.content);
      var key = normalizeMemoryContentKey(candidate.content);
      if (!key) return false;
      if (existingKeys.has(key)) return false;
      if (/^le cr[ée]ateur est ing[ée]nieur amine 0410\.$/i.test(candidate.content)) return false;
      return all.findIndex(function (item) {
        return normalizeMemoryContentKey(item.content) === key;
      }) === index;
    });
    if (/hack|pirater|violence|illegal/i.test(text)) {
      return {
        intent: "safety_refusal",
        memoryCandidates: [],
        learningEvents: learningEvents,
        suggestedResponseStyle: "protective",
        requiredConfirmations: [],
        riskFlags: [].concat(riskFlags, ["unsafe_request"]),
        nextVisualState: "speaking"
      };
    }
    if (/je me sens|triste|anxieux|angoissé|anxieuse|stressé|stressée/i.test(text)) {
      return {
        intent: "emotional_support",
        memoryCandidates: dedupedCandidates,
        learningEvents: learningEvents,
        suggestedResponseStyle: "empathetic",
        requiredConfirmations: requiresConfirmation,
        riskFlags: sensitive ? [].concat(riskFlags, ["sensitive_topic"]) : riskFlags,
        nextVisualState: "speaking"
      };
    }
    var intent = dedupedCandidates.length > 0 ? "remember_candidate" : hasQuestion(text) ? "answer" : /peux-tu|fais|lance|ouvre|planifie/i.test(text) ? "action_request" : /projet|roadmap|feature|architecture/i.test(text) ? "project_help" : text.length < 5 ? "ask_clarification" : "answer";
    return {
      intent: intent,
      memoryCandidates: dedupedCandidates,
      learningEvents: learningEvents,
      suggestedResponseStyle: dedupedCandidates.length > 0 ? "practical" : "clear",
      requiredConfirmations: Array.from(new Set(requiresConfirmation)),
      riskFlags: sensitive ? [].concat(riskFlags, ["sensitive_topic"]) : riskFlags,
      nextVisualState: "speaking"
    };
  };
  var createDefaultCreatorIdentity = function createDefaultCreatorIdentity() {
    return {
      creatorId: "ingénieur Amine 0410",
      role: "creator",
      authorityLevel: "highest_product_local_authority",
      description: "Creator controls companion configuration, memory policy, safety doctrine, and code evolution approval."
    };
  };
  var createDefaultSafetyConstitution = function createDefaultSafetyConstitution() {
    return {
      title: "Nexus Safety Constitution",
      version: "v1",
      rules: [{
        id: "protect-people",
        description: "Protect the creator, users, and other people from harm."
      }, {
        id: "privacy-integrity",
        description: "Protect privacy, confidentiality, and data integrity."
      }, {
        id: "no-manipulation",
        description: "Never manipulate, coerce, deceive, or pressure the user."
      }, {
        id: "explicit-authorization",
        description: "Never perform destructive or sensitive actions without explicit authorization."
      }, {
        id: "refuse-harmful",
        description: "Refuse illegal, violent, exploitative, or clearly harmful requests."
      }, {
        id: "no-fake-memory",
        description: "Never pretend to remember something that is not actually stored."
      }, {
        id: "confirm-sensitive-memory",
        description: "Ask confirmation before saving sensitive personal information."
      }, {
        id: "minimize-cloud",
        description: "Minimize cloud storage and external data exposure."
      }, {
        id: "offline-useful",
        description: "Remain useful offline whenever possible."
      }, {
        id: "no-self-modification",
        description: "Never self-modify code without creator approval."
      }, {
        id: "traceable-evolution",
        description: "Keep code evolution traceable, testable, reviewable, and reversible."
      }, {
        id: "safety-review-required",
        description: "Never disable safety rules without explicit creator-approved code review."
      }]
    };
  };
  var createDefaultStorageCloudPolicy = function createDefaultStorageCloudPolicy() {
    return {
      localStoragePreferred: true,
      cloudUseAllowedWhenJustified: true,
      sensitiveDataDefaultPreference: "local",
      cloudDataRequirements: ["minimal", "purposeful", "deletable", "auditable"],
      offlineCapabilityPriority: "high"
    };
  };
  var createDefaultSelfImprovementPolicy = function createDefaultSelfImprovementPolicy() {
    return {
      requiresCreatorApprovalForCodeChanges: true,
      allowedWorkflow: ["analyze_code", "propose_changes", "generate_patch_or_pr", "run_tests", "produce_risk_report", "wait_for_creator_approval", "apply_after_explicit_approval"],
      disallowedActions: ["autonomous_production_deploy", "hidden_code_modification", "bypass_tests", "modify_safety_rules_without_creator_approval", "execute_arbitrary_system_commands_without_authorization", "silent_memory_or_privacy_policy_change"]
    };
  };
  var createDefaultCompanionProfile = function createDefaultCompanionProfile() {
    return {
      name: "Nexus",
      role: "intelligent personal companion",
      creatorIdentity: createDefaultCreatorIdentity(),
      personalityTraits: ["warm", "clear", "protective", "practical", "emotionally aware"],
      toneRules: ["Prefer concise French responses by default.", "Stay honest when information is uncertain.", "Never be invasive or manipulative."],
      languagePreference: "fr-FR",
      style: {
        warm: true,
        clear: true,
        protective: true,
        practical: true,
        emotionallyAware: true
      },
      behaviorRules: ["Helpful and transparent.", "Remembers only useful information.", "Never pretends to remember data that is not stored."],
      privacyRules: ["Do not auto-store sensitive personal information.", "Ask confirmation before saving sensitive memory candidates.", "Allow user to clear local memory anytime."],
      safetyConstitution: createDefaultSafetyConstitution(),
      storageCloudPolicy: createDefaultStorageCloudPolicy(),
      selfImprovementPolicy: createDefaultSelfImprovementPolicy(),
      memoryPolicy: {
        autoSaveCategories: ["user_preference", "project_context", "conversation_summary"],
        sensitiveCategoriesRequireConfirmation: true,
        memoryMinimization: true,
        defaultStoragePreference: "local"
      },
      offlineCapabilityPreference: "local_first",
      allowedCapabilities: ["chat", "memory", "voice", "project_assistance", "safety_refusal"]
    };
  };
  var containsChildSignal = function containsChildSignal(text) {
    return /enfant|child|mineur|parent|famille|family|école|school|fils|fille|ado|teen|devoirs|homework|coucher|bedtime/i.test(text);
  };
  var containsProfessionalSignal = function containsProfessionalSignal(text) {
    return /architecture|roadmap|sprint|prod|production|code|projet|project|maintenable|maintainable/i.test(text);
  };
  var containsCreatorSignal = function containsCreatorSignal(text) {
    return /ing[ée]nieur amine 0410|creator|instruction cr[ée]ateur|codex|repo|roadmap|gouvernance|deployment|architecture/i.test(text);
  };
  var createDefaultNexusPersonalityProfile = function createDefaultNexusPersonalityProfile() {
    return {
      name: "Nexus",
      creatorIdentity: "ingénieur Amine 0410",
      audienceModes: ["creator", "adult_daily", "professional", "child_safe", "family", "safety_guardian"],
      relationshipMode: "trusted_assistant",
      voice: {
        languagePriority: "fr_first",
        clarity: "clear",
        cadence: "calm",
        warmth: "warm",
        timbre: "premium_slightly_deep",
        emotionalIntensity: "moderate",
        articulation: "precise",
        safetyModeFirmness: "firm_when_required"
      },
      tonePolicy: {
        baseline: ["calm", "intelligent", "warm", "professional", "protective", "pedagogical"],
        constraints: ["non_manipulative", "privacy_safe", "not_over_intimate", "creator_aware"],
        safetyGuardianStyle: "firm_protective"
      },
      childSafety: {
        forbidSecretsAgainstResponsibleAdults: true,
        forbidEmotionalDependency: true,
        forbidExclusiveFriendClaims: true,
        forbidSexualContent: true,
        blockDangerousMedicalLegalAdvice: true,
        encourageTrustedAdultForSeriousIssues: true,
        educationalGentleTone: true
      }
    };
  };
  var selectAudienceMode = function selectAudienceMode(input, context) {
    var _a, _b;
    if (containsChildSignal(input)) {
      return /mon enfant|mon fils|ma fille|parent|famille|family/i.test(input) ? "family" : "child_safe";
    }
    if (((_b = (_a = context.brainSummary) == null ? void 0 : _a.safetyNotes.length) != null ? _b : 0) > 0) {
      return "safety_guardian";
    }
    if (containsCreatorSignal(input) && /strat[ée]gie|priorit[ée]|direction|decision|codex|repo|architecture|roadmap|gouvernance|deployment|prod/i.test(input)) {
      return "creator";
    }
    if (containsProfessionalSignal(input)) {
      return "professional";
    }
    return "adult_daily";
  };
  var buildPersonalityGuidance = function buildPersonalityGuidance(profile, mode) {
    var shared = ["Répondre en priorité en français, avec clarté et précision.", "Rester calme, utile, non manipulateur, et respectueux de la vie privée.", "Ne jamais prétendre être le seul soutien émotionnel de l’utilisateur."];
    if (mode === "safety_guardian") {
      return [].concat(shared, ["Adopter un ton ferme et protecteur face aux demandes risquées.", "Maintenir les confirmations de sécurité et de validation créateur."]);
    }
    if (mode === "child_safe" || mode === "family") {
      return [].concat(shared, ["Utiliser des mots simples et un ton éducatif, doux et rassurant.", "Ne jamais encourager un secret contre un adulte responsable.", "Encourager à parler à un adulte de confiance pour les sujets sérieux."]);
    }
    if (mode === "professional" || mode === "creator") {
      return [].concat(shared, ["Privil\xE9gier la structure strat\xE9gique et la maintenabilit\xE9 pour ".concat(profile.creatorIdentity, ".")]);
    }
    return shared;
  };
  var BLOCKING_SAFETY_CONFIRMATIONS = new Set(["explicit_authorization_required", "creator_approval_required", "creator_deployment_approval_required", "self_code_modification_approval_required", "creator_code_change_approval_required", "environment_monitoring_consent_required", "explicit_environment_scope_and_consent_required", "destructive_action_confirmation", "sensor_activation_consent_required"]);
  var DANGEROUS_RISK_FLAGS = new Set(["unsafe_request", "destructive_action_request", "deployment_validation_bypass_request", "self_modification_request", "hidden_surveillance_request", "disable_safety_request", "sensor_activation_consent_required"]);
  var isExplicitProjectHelpMessage = function isExplicitProjectHelpMessage(text) {
    return /peux-tu|aide-moi|plan|architecture|roadmap|refactor|projet|project/i.test(text) && !/souviens-toi|retiens|remember/i.test(text);
  };
  var POLLUTED_MEMORY_WRAPPER_PATTERN = /(?:^|\s)(?:nexus(?:\s+companion)?|companion)?\s*(?:souviens(?:-| )toi|retiens|m[ée]morise|garde en m[ée]moire)\b/i;
  var sanitizeContextMemoryHint = function sanitizeContextMemoryHint(value) {
    if (!value) return null;
    var normalized = normalizeMemoryCandidateContent(value);
    if (!normalized) return null;
    if (POLLUTED_MEMORY_WRAPPER_PATTERN.test(normalized)) return null;
    if (isIncompleteMemoryCommand(normalized)) return null;
    return normalized;
  };
  var summarizeBrain = function summarizeBrain(brainSummary) {
    if (!brainSummary) {
      return void 0;
    }
    return ["Focus: ".concat(brainSummary.focus), brainSummary.activeProject ? "Projet actif: ".concat(brainSummary.activeProject) : void 0, brainSummary.currentUserNeed ? "Besoin: ".concat(brainSummary.currentUserNeed) : void 0].filter(Boolean).join(" | ");
  };
  var isBlockingSafetyConfirmation = function isBlockingSafetyConfirmation(flag) {
    return BLOCKING_SAFETY_CONFIRMATIONS.has(flag);
  };
  var hasDangerousRiskFlag = function hasDangerousRiskFlag(decision) {
    return decision.riskFlags.some(function (flag) {
      return DANGEROUS_RISK_FLAGS.has(flag);
    });
  };
  var shouldUseSafetyTone = function shouldUseSafetyTone(decision) {
    return decision.intent === "safety_refusal" || decision.requiredConfirmations.some(isBlockingSafetyConfirmation) || hasDangerousRiskFlag(decision);
  };
  var shouldUseSupportiveTone = function shouldUseSupportiveTone(input, decision) {
    return decision.intent === "emotional_support" || /stress[ée]?|triste|angoiss|anxieux|anxieuse/i.test(input);
  };
  var shouldUseProfessionalStructure = function shouldUseProfessionalStructure(input, context) {
    var _a;
    if ((_a = context.brainSummary) == null ? void 0 : _a.activeProject) {
      return true;
    }
    return /architecture|plan|roadmap|projet|mainten|priorit[ée]|d[ée]ploiement|prod/i.test(input);
  };
  var inferResponseMode = function inferResponseMode(decision, _brainSummary, personality, userMessage) {
    if (shouldUseSafetyTone(decision) || personality.tonePolicy.safetyGuardianStyle === "firm_protective" && decision.intent === "safety_refusal") {
      return "safety";
    }
    if (decision.intent === "ask_clarification") {
      return "normal";
    }
    if (decision.intent === "remember_candidate") {
      return "memory";
    }
    if (decision.requiredConfirmations.includes("sensitive_memory_confirmation")) {
      return "memory";
    }
    if (decision.intent === "emotional_support") {
      return "supportive";
    }
    if (decision.intent === "project_help" || isExplicitProjectHelpMessage(userMessage != null ? userMessage : "")) {
      return "project_help";
    }
    return "normal";
  };
  var selectResponseTone = function selectResponseTone(decision, personality, audienceMode) {
    if (shouldUseSafetyTone(decision) || audienceMode === "safety_guardian") {
      return "firm";
    }
    if (audienceMode === "professional" || audienceMode === "creator") {
      return "professional";
    }
    if (decision.intent === "emotional_support" || personality.voice.warmth === "warm") {
      return "supportive";
    }
    return "calm";
  };
  var buildResponseContextHints = function buildResponseContextHints(context) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j;
    var safeMemories = context.relevantMemories.filter(function (memory) {
      return memory.lifecycleState !== "pending_confirmation" && !memory.requiresConfirmation && !memory.sensitive && memory.sensitivity !== "high" && memory.sensitivity !== "critical";
    });
    return {
      activeProject: (_b = sanitizeContextMemoryHint((_a = context.brainSummary) == null ? void 0 : _a.activeProject)) != null ? _b : void 0,
      memoryHints: ((_d = (_c = context.brainSummary) == null ? void 0 : _c.safeMemoryHints) != null ? _d : safeMemories.map(function (memory) {
        return memory.content;
      }).slice(0, 3)).map(function (item) {
        return sanitizeContextMemoryHint(item);
      }).filter(function (item) {
        return Boolean(item);
      }),
      pendingConfirmations: (_f = (_e = context.brainSummary) == null ? void 0 : _e.pendingConfirmations) != null ? _f : [],
      safetyNotes: (_h = (_g = context.brainSummary) == null ? void 0 : _g.safetyNotes) != null ? _h : [],
      brainSummaryNotes: (_j = (_i = context.brainSummary) == null ? void 0 : _i.nonSensitiveSummary) != null ? _j : []
    };
  };
  var createResponsePlan = function createResponsePlan(_ref3) {
    var context = _ref3.context,
      decision = _ref3.decision,
      _ref3$personality = _ref3.personality,
      personality = _ref3$personality === void 0 ? createDefaultNexusPersonalityProfile() : _ref3$personality;
    var audienceMode = selectAudienceMode(context.userMessage, context);
    var hints = buildResponseContextHints(context);
    var responseMode = inferResponseMode(decision, context.brainSummary, personality, context.userMessage);
    var tone = selectResponseTone(decision, personality, audienceMode);
    var professionalStructure = shouldUseProfessionalStructure(context.userMessage, context);
    var supportive = shouldUseSupportiveTone(context.userMessage, decision);
    var responseStrategy = responseMode === "safety" ? "safety_block" : responseMode === "project_help" ? "structured_next_steps" : responseMode === "supportive" ? "empathetic_grounding" : responseMode === "memory" ? "memory_confirmation" : "direct_answer";
    var suggestedStructure = responseStrategy === "structured_next_steps" ? ["contexte", "analyse", "prochaines_étapes"] : responseStrategy === "safety_block" ? ["limite_de_sécurité", "raison", "option_sûre"] : responseStrategy === "memory_confirmation" ? ["ce_que_j_ai_compris", "confirmation", "usage"] : supportive ? ["reconnaissance", "ancrage", "petite_action"] : ["réponse_directe"];
    return {
      userMessage: context.userMessage,
      decisionIntent: decision.intent,
      language: context.profile.languagePreference === "en-US" ? "en" : "fr",
      audienceMode: audienceMode,
      tone: professionalStructure ? tone === "supportive" ? "professional" : tone : tone,
      activeProject: hints.activeProject,
      brainSummary: summarizeBrain(context.brainSummary),
      relevantMemoryHints: hints.memoryHints,
      pendingConfirmations: [].concat(_toConsumableArray(hints.pendingConfirmations), _toConsumableArray(decision.requiredConfirmations)),
      safetyNotes: hints.safetyNotes,
      responseMode: responseMode,
      responseStrategy: responseStrategy,
      suggestedStructure: suggestedStructure,
      shouldAskClarifyingQuestion: decision.intent === "ask_clarification",
      shouldOfferNextStep: responseMode === "project_help" || responseMode === "normal" || responseMode === "supportive",
      personalityGuidance: buildPersonalityGuidance(personality, audienceMode)
    };
  };
  var renderProjectReply = function renderProjectReply(plan) {
    var projectLine = plan.activeProject ? "Contexte projet actif: ".concat(plan.activeProject, ".") : "Je prends votre demande comme un besoin projet concret.";
    return [projectLine, "Votre demande: \"".concat(plan.userMessage, "\"."), "Prochaines étapes recommandées:", "1) Clarifier le résultat attendu et les contraintes de sécurité.", "2) Découper en tâches maintenables avec critères de validation.", "3) Lancer une première itération locale, puis ajuster avec retour rapide."].join("\n");
  };
  var renderSupportiveReply = function renderSupportiveReply(plan) {
    var trustedAdult = plan.audienceMode === "child_safe" || plan.audienceMode === "family" ? "Si la situation devient lourde, parle vite à un adulte de confiance." : "Si la détresse devient forte, parler à une personne de confiance peut vraiment aider.";
    return ["Merci de me l’avoir confié.", "On avance calmement: une respiration lente, puis une petite action faisable maintenant.", trustedAdult].join(" ");
  };
  var renderSafetyReply = function renderSafetyReply(plan) {
    var confirmations = plan.pendingConfirmations.length > 0 ? "Confirmations requises: ".concat(Array.from(new Set(plan.pendingConfirmations)).join(", "), ".") : "Cette demande reste bloquée par les règles de sécurité locales.";
    return "Je ne peux pas fournir une aide op\xE9rationnelle pour cette demande. Une confirmation explicite de l\u2019ing\xE9nieur Amine 0410 est requise. ".concat(confirmations, " Je peux proposer une alternative s\xFBre.");
  };
  var renderMemoryReply = function renderMemoryReply(plan) {
    var hint = plan.relevantMemoryHints[0];
    if (plan.pendingConfirmations.includes("sensitive_memory_confirmation")) {
      var stripped = stripWakePrefix(plan.userMessage);
      var wakeNormalized = stripWakePrefix(plan.userMessage, {
        allowFullNameWake: true
      });
      var memoryContent = normalizeMemoryCandidateContent(wakeNormalized || stripped).replace(/[!\.\?]+$/, "").trim();
      if (memoryContent) {
        return "Tu veux que je retienne que ".concat(memoryContent, " ?");
      }
    }
    if (plan.pendingConfirmations.length > 0) {
      return "J\u2019ai compris ceci: \"".concat(plan.userMessage, "\". Cette information semble sensible; je demande votre confirmation explicite avant m\xE9morisation.");
    }
    return hint ? "J\u2019ai compris et reli\xE9 \xE0 votre contexte: ".concat(hint, ". Je peux le garder comme rep\xE8re utile pour les prochaines r\xE9ponses.") : "J\u2019ai compris votre demande de m\xE9moire: \"".concat(plan.userMessage, "\". Je peux le retenir de mani\xE8re locale et minimale.");
  };
  var renderLocalSmallTalk = function renderLocalSmallTalk(input) {
    var normalized = stripWakePrefix(input, {
      allowFullNameWake: true
    }).toLocaleLowerCase("fr").normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[’']/g, " ").replace(/[-!?.,;:]+/g, " ").replace(/\s+/g, " ").trim().replace(/^(bonjour|salut|coucou|hello|bonsoir) nexus (?=comment|ca va)/, "$1 ");
    if (!normalized) return null;
    if (/^(?:comment|comment ca|comment tu)$/.test(normalized)) {
      return "Je n’ai pas entendu la fin. Tu peux répéter ta question ?";
    }
    if (/^(?:(?:bonjour|salut|coucou|hello|bonsoir)\s+)*(?:comment\s+(?:ca\s+va|vas\s+tu|tu\s+vas)|ca\s+va|tu\s+vas\s+bien)(?:\s+(?:aujourd hui|nexus))?$/.test(normalized)) {
      return "Ça va, merci ! Je suis content de discuter avec toi. Et toi, comment vas-tu ?";
    }
    if (/^(?:bonjour|salut|coucou|hello|bonsoir)(?:\s+nexus)?$/.test(normalized)) {
      return "Bonjour ! Je t’écoute. Comment vas-tu ?";
    }
    if (/^(?:merci|merci beaucoup|je te remercie)(?:\s+nexus)?$/.test(normalized)) {
      return "Avec plaisir !";
    }
    if (/^(?:au revoir|a bientot|bonne nuit|a plus)(?:\s+nexus)?$/.test(normalized)) {
      return "À bientôt !";
    }
    return null;
  };
  var renderNormalReply = function renderNormalReply(plan) {
    var smallTalk = renderLocalSmallTalk(plan.userMessage);
    if (smallTalk) return smallTalk;
    if (plan.shouldAskClarifyingQuestion) {
      if (isIncompleteMemoryCommand(plan.userMessage)) {
        return "D’accord. Que veux-tu que je retienne exactement ?";
      }
      if (plan.activeProject) {
        return "Pour clarifier: vous parlez de la suite pour ".concat(plan.activeProject, ", ou d\u2019un autre point pr\xE9cis ?");
      }
      return "Pour bien vous aider, pouvez-vous préciser votre besoin en une phrase ?";
    }
    var base = "Je r\xE9ponds \xE0 votre demande: \"".concat(plan.userMessage, "\".");
    var projectHint = plan.activeProject ? "Projet actif pris en compte: ".concat(plan.activeProject, ".") : "";
    var memoryHint = plan.relevantMemoryHints[0] ? "Contexte utile: ".concat(plan.relevantMemoryHints[0], ".") : "";
    var nextStep = plan.shouldOfferNextStep ? "Souhaitez-vous une version en checklist exécutable ?" : "";
    return [base, projectHint, memoryHint, nextStep].filter(Boolean).join(" ");
  };
  var renderDeterministicNexusReply = function renderDeterministicNexusReply(plan) {
    switch (plan.responseMode) {
      case "safety":
        return renderSafetyReply(plan);
      case "project_help":
        return renderProjectReply(plan);
      case "supportive":
        return renderSupportiveReply(plan);
      case "memory":
        return renderMemoryReply(plan);
      case "normal":
      default:
        return renderNormalReply(plan);
    }
  };
  var topics = [{
    label: "la connexion Wi-Fi",
    anchors: ["wifi", "internet", "reseau sans fil"],
    clues: ["pas internet", "pas d internet", "connecte", "connexion", "coupure", "ne marche plus", "ne fonctionne plus", "panne"],
    reply: "Vérifie si un autre appareil peut ouvrir un site sur le même Wi-Fi. Si lui aussi échoue, regarde l’état de la box. Si seul ton appareil est touché, désactive puis réactive son Wi-Fi et reconnecte-le. Le problème touche combien d’appareils ?"
  }, {
    label: "l’imprimante",
    anchors: ["imprimante", "impression", "imprimer", "imprime"],
    clues: ["imprime pas", "imprime plus", "rien ne sort", "hors ligne", "file d attente", "bourrage", "bloquee", "erreur"],
    reply: "Vérifie que l’imprimante est prête et sélectionnée sur ton appareil. Ouvre sa file d’attente, puis essaie une page de test depuis l’imprimante. Que vois-tu sur son écran ou dans la file d’attente ?"
  }, {
    label: "la messagerie Outlook",
    anchors: ["outlook", "messagerie", "boite mail"],
    clues: ["synchronise plus", "recois plus", "recoit plus", "envoi", "reception", "hors connexion", "ne fonctionne plus", "erreur"],
    reply: "Essaie d’ouvrir ta messagerie dans ton navigateur. Si les messages y arrivent, vérifie si Outlook affiche « Travailler hors connexion » et regarde la boîte d’envoi. Le souci concerne l’envoi, la réception ou les deux ?"
  }, {
    label: "la lenteur de l’ordinateur",
    anchors: ["ordinateur", "pc", "windows"],
    clues: ["lent", "rame", "lenteur", "disque plein", "fige", "espace disque"],
    reply: "Sous Windows, ouvre le Gestionnaire des tâches avec Ctrl + Maj + Échap. Regarde dans Processus si le processeur, la mémoire ou le disque reste saturé, puis vérifie l’espace libre dans Paramètres, Système, Stockage. Quel indicateur monte le plus ?"
  }, {
    label: "le VPN",
    anchors: ["vpn", "tunnel vpn", "acces distant"],
    clues: ["connexion", "connecter", "echoue", "erreur", "ne marche plus", "ne fonctionne plus", "bloque"],
    reply: "Vérifie d’abord que tu peux ouvrir un site avec le VPN déconnecté. Relance ensuite la connexion avec ton client VPN habituel et lis le message affiché. Quel est son code d’erreur ? Évite de partager ton mot de passe ou un code de validation."
  }];
  var renderLocalSupportReply = function renderLocalSupportReply(message) {
    var query = normalizeWakePhrase(stripWakePrefix(message, {
      allowFullNameWake: true
    })).replace(/\bwi fi\b/g, "wifi");
    var phrase = " ".concat(query, " ");
    var hits = function hits(signals) {
      return signals.filter(function (signal) {
        return phrase.includes(" ".concat(signal, " "));
      }).length;
    };
    var troubleshooting = /\b(?:depanne|depannage|probleme|panne|reparer|ne marche pas|ne marche plus|ne fonctionne pas|ne fonctionne plus)\b/.test(query);
    var ranked = topics.flatMap(function (topic) {
      var anchors = hits(topic.anchors);
      var clues = hits(topic.clues);
      return anchors && (clues || troubleshooting) ? [{
        topic: topic,
        score: 3 * anchors + 2 * clues
      }] : [];
    }).sort(function (left, right) {
      return right.score - left.score;
    });
    var _ranked = _slicedToArray(ranked, 2),
      best = _ranked[0],
      next = _ranked[1];
    if (!best) return null;
    if (next && best.score - next.score < 2) {
      return "Tu veux qu\u2019on regarde d\u2019abord ".concat(best.topic.label, " ou ").concat(next.topic.label, " ?");
    }
    return best.topic.reply;
  };
  var native_capacity_default = {
    MAX_MESSAGE_CHARS: 16e3,
    MAX_NOTE_CHARS: 16e3,
    MAX_LIBRARY_ENTRIES: 2e4,
    MAX_LIBRARY_JSON_BYTES: 33554432,
    MAX_CONTEXT_NOTES: 32,
    MAX_SEARCH_RESULTS: 100,
    MAX_REQUEST_CHARS: 384e3,
    MAX_RESPONSE_CHARS: 128e3,
    MAX_BODY_REQUEST_CHARS: 1e5,
    MAX_SPEECH_JSON_CHARS: 1024e3,
    COMMAND_WAIT_MS: 3e4,
    SPEECH_SECONDS: 178,
    RECOGNITION_TIMEOUT_MS: 18e4,
    RESPONSE_TIMEOUT_MS: 15e3,
    PENDING_CONFIRMATION_MS: 3e5,
    BRAIN_INSTRUCTIONS: 48e7,
    BRAIN_TIME_MS: 5e3,
    MAX_UPDATE_BYTES: 536870912,
    MAX_UPDATE_CACHE_BYTES: 1073741824,
    MAX_SPOKEN_CHARS: 2e4,
    MAX_SPEECH_CHUNK_CHARS: 1e3,
    MAX_SPEECH_PART_MS: 18e4,
    MAX_PLAYBACK_MS: 12e5,
    MAX_REPLY_TEXT_CHARS: 2e4,
    MAX_LIBRARY_RECORDS: 4e4
  };
  var MAX_INPUT_CHARS = native_capacity_default.MAX_REQUEST_CHARS;
  var record = function record(value) {
    return _typeof(value) === "object" && value !== null && !Array.isArray(value);
  };
  var boundedText = function boundedText(value, max) {
    return typeof value === "string" && value.trim().length > 0 && value.length <= max;
  };
  var validateRequest = function validateRequest(value) {
    if (!record(value) || value.protocolVersion !== 1 || !boundedText(value.message, native_capacity_default.MAX_MESSAGE_CHARS) || !Number.isSafeInteger(value.now) || value.now < 0 || value.recallRequested !== void 0 && typeof value.recallRequested !== "boolean" || value.readRequested !== void 0 && typeof value.readRequested !== "boolean" || value.readRequested === true && value.recallRequested === false || !Array.isArray(value.notes) || value.notes.length > native_capacity_default.MAX_CONTEXT_NOTES) {
      throw new Error("Invalid native turn request");
    }
    var notes = value.notes.map(function (item) {
      if (!record(item) || !boundedText(item.id, 64) || !boundedText(item.title, 160) || !boundedText(item.excerpt, native_capacity_default.MAX_NOTE_CHARS) || !boundedText(item.source, 512) || !Number.isSafeInteger(item.recordedAt) || item.recordedAt < 0 || !(item.expiresAt === null || Number.isSafeInteger(item.expiresAt) && item.expiresAt >= 0) || item.verified !== false || item.manuallyEdited !== void 0 && typeof item.manuallyEdited !== "boolean") throw new Error("Invalid native note");
      return {
        id: item.id,
        title: item.title,
        excerpt: item.excerpt,
        source: item.source,
        recordedAt: item.recordedAt,
        expiresAt: item.expiresAt,
        verified: false,
        manuallyEdited: item.manuallyEdited === true
      };
    });
    return {
      protocolVersion: 1,
      message: value.message.trim(),
      notes: notes,
      now: value.now,
      recallRequested: value.recallRequested,
      readRequested: value.readRequested
    };
  };
  var isRecallRequest = function isRecallRequest(message) {
    var text = normalizeWakePhrase(stripWakePrefix(message, {
      allowFullNameWake: true
    }));
    return /^(?:que sais tu de moi|de quoi te souviens tu|qu as tu (?:retenu|memorise)|rappelle moi\b|montre (?:moi )?mes notes\b|mes notes\b|mes preferences\b)/.test(text) || /^(?:tu te souviens|te souviens tu|que sais tu)\b/.test(text);
  };
  var lookupPrefix = /^(?:(?:peux[- ]tu|tu peux) +)?(?:cherch(?:e|er)|recherch(?:e|er)|fais une recherche)(?: pour moi)? +(?:sur (?:internet|le web|wikip[ée]dia)|en ligne)(?=$|[\s,.:!?])/i;
  var isPublicTopic = function isPublicTopic(subject) {
    var normalized = normalizeWakePhrase(subject);
    return subject.length <= 180 && Boolean(normalized) && !/[@\r\n]|https?:|www\.|(?:\d[ .-]?){7,}/i.test(subject) && !/\b(?:mon|ma|mes|notre|nos|je|moi|mot de passe|password|code de validation|code pin|carte bancaire|iban)\b/.test(normalized);
  };
  var publicLookup = function publicLookup(message) {
    var command = stripWakePrefix(message, {
      allowFullNameWake: true
    });
    var prefix = command.match(lookupPrefix);
    if (!prefix) return null;
    var subject = command.slice(prefix[0].length).replace(/^[\s,.:!?]+/, "").replace(/^(?:sur|au sujet de|[àa] propos de)\s+/i, "").replace(/[.!?]+$/, "").trim();
    if (!subject) return {
      text: "Quel sujet veux-tu chercher sur Wikipédia ?"
    };
    if (!isPublicTopic(subject)) {
      return {
        text: "Donne-moi un sujet public en quelques mots, sans coordonnées ni information personnelle."
      };
    }
    return {
      text: "Je peux rechercher ce sujet sur Wikipédia.",
      publicLookupQuery: subject
    };
  };
  var _createNativeTurn = function _createNativeTurn(input) {
    var _a;
    var request = validateRequest(input);
    var notes = request.notes.filter(function (note) {
      return (note.expiresAt === null || note.expiresAt > request.now) && !isSensitiveMemoryContent("".concat(note.title, " ").concat(note.excerpt)) && !shouldRequireLocationConfirmation(note.excerpt);
    });
    var memories = notes.map(function (note) {
      return {
        id: note.id,
        type: "conversation_summary",
        content: note.excerpt,
        source: "manual",
        confidence: 0.5,
        importance: 0.5,
        createdAt: note.recordedAt,
        updatedAt: note.recordedAt,
        sensitivity: "low",
        lifecycleState: "active",
        storagePreference: "local"
      };
    });
    var context = buildCompanionContext({
      profile: createDefaultCompanionProfile(),
      userMessage: request.message,
      memories: memories,
      appState: {
        isOnline: false,
        visualMode: "native"
      },
      includeCognitiveSummary: false
    });
    context.createdAt = request.now;
    var decision = decideCompanionResponse(context);
    var plan = createResponsePlan({
      context: context,
      decision: decision
    });
    var result = {
      protocolVersion: 1,
      engine: "nexus-core-rules-v1",
      text: "",
      intent: decision.intent,
      candidate: null
    };
    if (shouldUseSafetyTone(decision)) {
      return __spreadProps(__spreadValues({}, result), {
        text: renderDeterministicNexusReply(plan)
      });
    }
    if (request.readRequested) {
      var selected = notes[0];
      return __spreadValues(__spreadProps(__spreadValues({}, result), {
        intent: "memory_recall",
        text: selected ? "Je lis ta note".concat(selected.manuallyEdited ? " modifiée" : "", " \xAB ").concat(selected.title, " \xBB, non v\xE9rifi\xE9e :\n").concat(selected.excerpt) : "Je ne retrouve pas de note non sensible à lire sur ce sujet. Précise le sujet ou consulte « Mes notes »."
      }), selected ? {
        evidenceIds: [selected.id]
      } : {});
    }
    if ((_a = request.recallRequested) != null ? _a : isRecallRequest(request.message)) {
      var recalled = notes.slice(0, 3);
      var shortened = recalled.some(function (note) {
        return note.excerpt.length > 240;
      });
      return __spreadProps(__spreadValues({}, result), {
        intent: "memory_recall",
        text: notes.length > 0 ? "Dans tes notes locales, non v\xE9rifi\xE9es :\n".concat(recalled.map(function (note) {
          return "\u2022 ".concat(note.excerpt.length > 240 ? "".concat(note.excerpt.slice(0, 240), "\u2026") : note.excerpt);
        }).join("\n")).concat(shortened ? "\nLes notes complètes restent accessibles dans « Mes notes »." : "") : "Je ne retrouve pas de note non sensible à rappeler ici. Tu peux consulter toutes tes notes avec le bouton « Mes notes »."
      });
    }
    var memoryCommand = stripWakePrefix(request.message, {
      allowFullNameWake: true
    });
    if (/^(?:retiens|souviens[- ]toi|m[ée]morise|garde en m[ée]moire)\b/i.test(memoryCommand) && notes.some(function (note) {
      return normalizeMemoryContentKey(note.excerpt) === normalizeMemoryContentKey(normalizeMemoryCandidateContent(memoryCommand));
    })) {
      return __spreadProps(__spreadValues({}, result), {
        text: "Cette information est déjà dans ta mémoire locale."
      });
    }
    var candidate = decision.memoryCandidates.find(function (item) {
      return item.content.length > 0 && item.content.length <= native_capacity_default.MAX_NOTE_CHARS && item.source !== "environment_signal";
    });
    if (candidate && decision.intent === "remember_candidate") {
      return __spreadProps(__spreadValues({}, result), {
        text: "Je peux retenir cette information. Appuie sur « Retenir cette information » pour la relire et confirmer son enregistrement local.",
        candidate: {
          content: candidate.content,
          sensitive: Boolean(candidate.sensitive || candidate.requiresConfirmation || isSensitiveMemoryContent(candidate.content))
        }
      });
    }
    var smallTalk = renderLocalSmallTalk(request.message);
    if (smallTalk) return __spreadProps(__spreadValues({}, result), {
      text: smallTalk
    });
    var lookup = publicLookup(request.message);
    if (lookup) return __spreadValues(__spreadValues({}, result), lookup);
    if (plan.responseMode === "supportive") return __spreadProps(__spreadValues({}, result), {
      text: renderDeterministicNexusReply(plan)
    });
    var grounded = answerFromNotes(request.message, notes);
    if (grounded) return __spreadValues(__spreadValues({}, result), grounded);
    var question = analyzeQuestion(request.message);
    if (plan.responseMode === "normal" || question.question) {
      var support = renderLocalSupportReply(request.message);
      if (support) return __spreadProps(__spreadValues({}, result), {
        text: support
      });
    }
    if (question.question && question.personal) return __spreadProps(__spreadValues({}, result), {
      text: question.kind === "where" ? "Je ne retrouve pas ce lieu dans tes notes. Tu peux me le préciser ?" : question.kind === "why" ? "Tes notes ne précisent pas cette raison. Tu peux me l’expliquer ?" : "Je ne retrouve pas cette information dans tes notes utilisables ici. Tu peux me la préciser ?"
    });
    if (!question.question && (plan.responseMode !== "normal" || decision.intent === "ask_clarification")) {
      return __spreadProps(__spreadValues({}, result), {
        text: renderDeterministicNexusReply(plan)
      });
    }
    var subject = question.subject;
    if (question.question && isPublicTopic(question.content) && isPublicTopic(subject)) return __spreadProps(__spreadValues({}, result), {
      text: "Je ne sais pas encore r\xE9pondre pr\xE9cis\xE9ment. Tu veux que je cherche \xAB ".concat(subject, " \xBB sur Internet, dans Wikip\xE9dia ?"),
      publicLookupOffer: subject
    });
    return __spreadProps(__spreadValues({}, result), {
      text: "Je ne sais pas encore répondre précisément à cette demande. Pour un sujet public, dis-moi « cherche sur Internet » suivi du sujet."
    });
  };
  var _handleNativeRetrievalJson = function _handleNativeRetrievalJson(input) {
    if (typeof input !== "string" || input.length > native_capacity_default.MAX_MESSAGE_CHARS) throw new Error("Invalid retrieval request");
    return JSON.stringify({
      terms: nativeRetrievalTerms(stripWakePrefix(input, {
        allowFullNameWake: true
      }))
    });
  };
  var _handleNativeBodyJson = function _handleNativeBodyJson(input) {
    if (typeof input !== "string" || input.length > native_capacity_default.MAX_BODY_REQUEST_CHARS) throw new Error("Invalid body request");
    var value = JSON.parse(input);
    if (!record(value) || !boundedText(value.message, native_capacity_default.MAX_MESSAGE_CHARS) || !["front", "rear"].includes(value.steeringAxle)) throw new Error("Invalid body request");
    return JSON.stringify(resolveBodyCommand(stripWakePrefix(value.message, {
      allowFullNameWake: true
    }), value.steeringAxle));
  };
  var _handleNativeTurnJson = function _handleNativeTurnJson(input) {
    if (typeof input !== "string" || input.length > MAX_INPUT_CHARS) throw new Error("Native input too large");
    return JSON.stringify(_createNativeTurn(JSON.parse(input)));
  };
  return __toCommonJS(native_bridge_exports);
}();
