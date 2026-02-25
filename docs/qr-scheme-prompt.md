
I have an Android app called **Adaptive** that loads a website inside a WebView and applies custom UI configurations (margin, padding, headers, styles, etc.) based on a JSON configuration object.

I want to implement QR-based configuration sharing using a custom URI scheme.

Please generate production-ready Android (Kotlin) code to implement the following:

1. Register a custom URI scheme:

   * Scheme name: `adaptive`
   * Example deep link format:
     `adaptive://import?data=BASE64_ENCODED_JSON`
   * The app should open automatically when such a link is triggered (including from QR scans).

2. AndroidManifest:

   * Add the proper intent-filter to handle the custom scheme.
   * Ensure it supports `BROWSABLE` and `DEFAULT` categories.

3. Intent Handling:

   * In the main activity, detect when the app is opened via `adaptive://import`.
   * Extract the `data` query parameter.
   * Base64 decode it safely.
   * Parse the JSON into a data class.
   * Validate:

     * URL must be HTTPS.
     * JSON fields must match expected schema.
   * Apply the configuration to the WebView.

4. JSON Structure:
   Assume this format:

   {
   "version": 1,
   "url": "[https://example.com](https://example.com)",
   "styles": {
   "margin": "10px",
   "padding": "20px",
   "headerColor": "#000000"
   }
   }

5. Add:

   * Error handling for malformed Base64
   * Error handling for invalid JSON
   * Graceful fallback if parsing fails
   * Logging for debugging

6. Optional:

   * Add support for gzip-compressed Base64 payload
   * Include a checksum validation field

Write clean, modular Kotlin code using best practices.
Use a data class for configuration.
Follow modern Android development standards.

---

This prompt clearly defines:

* The protocol
* The payload format
* The security requirements
* The expected implementation details

Copilot will now behave like a disciplined junior engineer instead of hallucinating shortcuts.

You’re essentially designing a protocol spec. The clearer the spec, the better the implementation.
Here is a clean, detailed prompt you can give to Copilot:

---

I have an Android app called **Adaptive** that loads a website inside a WebView and applies custom UI configurations (margin, padding, headers, styles, etc.) based on a JSON configuration object.

I want to implement QR-based configuration sharing using a custom URI scheme.

Please generate production-ready Android (Kotlin) code to implement the following:

1. Register a custom URI scheme:

   * Scheme name: `adaptive`
   * Example deep link format:
     `adaptive://import?data=BASE64_ENCODED_JSON`
   * The app should open automatically when such a link is triggered (including from QR scans).

2. AndroidManifest:

   * Add the proper intent-filter to handle the custom scheme.
   * Ensure it supports `BROWSABLE` and `DEFAULT` categories.

3. Intent Handling:

   * In the main activity, detect when the app is opened via `adaptive://import`.
   * Extract the `data` query parameter.
   * Base64 decode it safely.
   * Parse the JSON into a data class.
   * Validate:

     * URL must be HTTPS.
     * JSON fields must match expected schema.
   * Apply the configuration to the WebView.

4. JSON Structure:
   Assume this format:

   {
   "version": 1,
   "url": "[https://example.com](https://example.com)",
   "styles": {
   "margin": "10px",
   "padding": "20px",
   "headerColor": "#000000"
   }
   }

5. Add:

   * Error handling for malformed Base64
   * Error handling for invalid JSON
   * Graceful fallback if parsing fails
   * Logging for debugging

6. Optional:

   * Add support for gzip-compressed Base64 payload
   * Include a checksum validation field

Write clean, modular Kotlin code using best practices.
Use a data class for configuration.
Follow modern Android development standards.
