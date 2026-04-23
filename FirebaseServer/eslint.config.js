const tseslint = require("@typescript-eslint/eslint-plugin");
const tsparser = require("@typescript-eslint/parser");

module.exports = [
  {
    files: ["src/**/*.ts", "test/**/*.ts"],
    languageOptions: {
      parser: tsparser,
      parserOptions: {
        project: "./tsconfig.json",
      },
    },
    plugins: {
      "@typescript-eslint": tseslint,
    },
    rules: {
      // --- Strict errors (from tslint) ---

      // Do not allow the subtle/obscure comma operator.
      "no-sequences": "error",

      // Do not allow parameters to be reassigned.
      "no-param-reassign": "error",

      // Disallows the (often typo) syntax if (var1 = var2).
      "no-cond-assign": "error",

      // Disallows constructors for primitive types.
      "no-new-wrappers": "error",

      // Do not allow the same case to appear more than once in a switch block.
      "no-duplicate-case": "error",

      // Disallows a variable definition in an inner scope from shadowing a variable in an outer scope.
      // Allow enum members to shadow (SensorEventType enum values match factory function names).
      "no-shadow": "off",
      "@typescript-eslint/no-shadow": ["error", { "ignoreOnInitialization": true, "allow": [
        "Unknown", "ErrorSensorConflict", "Closed", "Closing", "ClosingTooLong",
        "Open", "OpenMisaligned", "Opening", "OpeningTooLong",
      ] }],

      // Empty blocks are almost never needed. Allow empty catch blocks.
      "no-empty": ["error", { "allowEmptyCatch": true }],

      // Functions must either be handled directly or returned.
      // This is a major source of errors in Cloud Functions.
      "@typescript-eslint/no-floating-promises": "error",

      // Do not allow strings to be thrown because they will not include stack traces.
      "no-throw-literal": "off",
      "@typescript-eslint/only-throw-error": "error",

      // Disallow control flow statements in finally blocks.
      "no-unsafe-finally": "error",

      // Disallow duplicate imports in the same file.
      "no-duplicate-imports": "error",

      // --- Strong Warnings (from tslint) ---

      // Warn when variables are defined with var.
      "no-var": "warn",

      // Prefer === and !== over == and !=.
      "eqeqeq": ["warn", "always"],

      // --- Light Warnings (from tslint) ---

      // Prefer const for values that will not change.
      "prefer-const": "warn",

      // Multi-line trailing commas help avoid merge conflicts.
      "comma-dangle": ["warn", "always-multiline"],

      // --- Relaxed rules ---

      // Allow explicit any for legacy code (tighten over time).
      "@typescript-eslint/no-explicit-any": "off",

      // Allow require imports (used in some legacy patterns).
      "@typescript-eslint/no-require-imports": "off",

      // Allow unused vars with underscore prefix.
      "@typescript-eslint/no-unused-vars": ["warn", {
        "argsIgnorePattern": "^_",
        "varsIgnorePattern": "^_",
      }],

      // Phase 4 of the database refactor (see
      // docs/FIREBASE_DATABASE_REFACTOR.md). Every Firestore collection
      // is wrapped by a typed singleton in src/database/; instantiating
      // TimeSeriesDatabase anywhere else reintroduces the duplication
      // pattern the refactor removed. Allowlisted paths below.
      "no-restricted-syntax": ["error", {
        "selector": "NewExpression[callee.name='TimeSeriesDatabase']",
        "message": "Do not instantiate TimeSeriesDatabase outside src/database/. Import the singleton for your collection (e.g., `import { DATABASE as FooDatabase } from '../database/FooDatabase'`). See docs/FIREBASE_DATABASE_REFACTOR.md.",
      }],
    },
  },
  {
    // The canonical home for TimeSeriesDatabase wrappers — one module
    // per collection. Direct instantiation here is the whole point.
    files: ["src/database/**/*.ts"],
    rules: {
      "no-restricted-syntax": "off",
    },
  },
  {
    // Contract test for TimeSeriesDatabase itself needs to instantiate
    // it directly. Other database tests use fakes, not the wrapper.
    files: ["test/database/TimeSeriesDatabaseTest.ts"],
    rules: {
      "no-restricted-syntax": "off",
    },
  },
  {
    ignores: ["lib/", "node_modules/"],
  },
];
