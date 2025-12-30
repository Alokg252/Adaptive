# App Configuration Guide

## How to Customize Your App

Edit `app_config.json` in the project root before building.

### Configuration Options

#### App Name
```json
{
  "appName": "My Custom App"
}
```

#### Icon - Option 1: Text-Based Icon
Create an icon with custom text, colors, and style:
```json
{
  "appName": "My App",
  "icon": {
    "type": "text",
    "text": "MA",
    "backgroundColor": "#6200EE",
    "textColor": "#FFFFFF",
    "fontSize": 64,
    "fontStyle": "bold",
    "cornerRadius": 25
  }
}
```

**Parameters:**
- `text`: 1-3 characters (initials, emoji, etc.)
- `backgroundColor`: Hex color for background
- `textColor`: Hex color for text
- `fontSize`: Size of text (48-80 recommended)
- `fontStyle`: "normal", "bold", or "italic"
- `cornerRadius`: 0 = square, 50 = circle (percentage)

#### Icon - Option 2: URL Icon
Download icon from a URL:
```json
{
  "appName": "My App",
  "icon": {
    "type": "url",
    "url": "https://example.com/my-icon.png"
  }
}
```

**Requirements:**
- Image should be square (512x512 recommended)
- Supported formats: PNG, JPG, WEBP
- URL must be accessible during build

### Build Steps

1. Edit `app_config.json` with your settings
2. Run the config script: `gradlew generateAppConfig`
3. Build the APK: `gradlew assembleDebug` or `gradlew assembleRelease`
4. Install and enjoy your customized app!

### Example Configurations

**Blue Business App:**
```json
{
  "appName": "WorkFlow Pro",
  "icon": {
    "type": "text",
    "text": "WP",
    "backgroundColor": "#1976D2",
    "textColor": "#FFFFFF",
    "fontSize": 56,
    "fontStyle": "bold",
    "cornerRadius": 20
  }
}
```

**Custom Logo from URL:**
```json
{
  "appName": "My Brand",
  "icon": {
    "type": "url",
    "url": "https://mybrand.com/logo-512.png"
  }
}
```

**Minimal Dark:**
```json
{
  "appName": "Reader",
  "icon": {
    "type": "text",
    "text": "R",
    "backgroundColor": "#000000",
    "textColor": "#FFFFFF",
    "fontSize": 72,
    "fontStyle": "normal",
    "cornerRadius": 50
  }
}
```
