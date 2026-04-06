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
    },
  },
  {
    ignores: ["lib/", "node_modules/"],
  },
];
