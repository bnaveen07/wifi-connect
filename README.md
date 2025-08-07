# WiFi Connect - Intelligent WiFi Management

An advanced Android application built with Jetpack Compose that provides seamless, intelligent WiFi connection management based on signal strength and user preferences. The app automatically switches between available WiFi networks to maintain the strongest possible connection while securely storing network credentials.

## Features

### Core Functionality

- **Intelligent WiFi Management**: Advanced background service with smart connection algorithms
- **Signal Strength-Based Switching**: Automatically connects to networks with optimal signal strength
- **Secure Password Storage**: Encrypted storage of WiFi passwords for automatic reconnection
- **Seamless Connection Handling**: Manages connection delays, failures, and reconnections gracefully
- **Real-time Network Monitoring**: Continuous scanning and evaluation with adaptive intervals

### Enhanced User Interface

- **Modern Material Design 3**: Beautiful, responsive interface with smooth animations
- **Advanced Network Cards**: Detailed network information with signal strength visualization
- **Interactive Connection Status**: Real-time updates with animated indicators
- **Smart Service Controls**: Comprehensive settings and configuration options
- **Enhanced Password Dialog**: Secure password entry with save options

### Technical Features

- **Foreground Service**: Ensures continuous operation with intelligent battery optimization
- **Comprehensive Permission Management**: Handles all necessary WiFi and location permissions
- **Android API Compatibility**: Full support for Android 10+ with legacy fallback
- **Encrypted Credential Storage**: AES-256 encryption for stored WiFi passwords
- **Connection Scoring Algorithm**: Multi-factor network evaluation system

## Enhanced Architecture

### Intelligent Connection Logic

- **Connection Scoring**: Evaluates networks based on:

  - Signal strength (primary factor)
  - Frequency band preference (5GHz preferred)
  - Saved password availability
  - User-defined network priority
  - Historical connection success

- **Smart Switching Criteria**:
  - Minimum signal threshold: -75 dBm (improved from -70 dBm)
  - Signal improvement requirement: 8 dBm difference
  - Connection stability period: 15 seconds before considering switch
  - Frequency band preference with 5GHz bonus
  - Automatic retry limit: 3 attempts per network

### Service Layer Enhancements

- `WiFiManagerService`: Enhanced core service with intelligent algorithms
- Optimized scanning intervals: 8 seconds for better responsiveness
- Improved connection timeout: 25 seconds with better error handling
- Connection attempt tracking and failure prevention
- Advanced network callback handling

### UI Layer Improvements

- **Enhanced Components**:
  - `EnhancedNetworkCard`: Animated cards with detailed network info
  - `SmartServiceControlCard`: Advanced settings and configuration
  - `SecurePasswordDialog`: Enhanced password input with save options
  - `AnimatedSignalIndicator`: Real-time signal strength visualization

### Data Management

- `WiFiPasswordManager`: Secure credential storage with AES encryption
- Enhanced `WiFiNetwork` model with scoring and frequency band detection
- Improved `WiFiConnectionState` with comprehensive status tracking
- Advanced security type detection (WPA3, WPA2, WPA, WEP, Open)

## Installation and Setup

### Prerequisites

- Android Studio Arctic Fox or newer
- Android SDK API level 34+
- Device with Android 14+ (API 34+)
- Location services enabled

### Building the Project

1. Clone the repository
2. Open in Android Studio
3. Sync project with Gradle files
4. Build and run on device or emulator

```bash
./gradlew assembleDebug
```

### Enhanced Permissions

The app requests the following permissions:

- `ACCESS_FINE_LOCATION` - Required for WiFi scanning
- `ACCESS_COARSE_LOCATION` - Backup location permission
- `ACCESS_WIFI_STATE` - Read WiFi state
- `CHANGE_WIFI_STATE` - Modify WiFi connections
- `ACCESS_NETWORK_STATE` - Monitor network changes
- `CHANGE_NETWORK_STATE` - Modify network settings
- `FOREGROUND_SERVICE` - Run background service
- `FOREGROUND_SERVICE_CONNECTED_DEVICE` - Device connectivity service
- `POST_NOTIFICATIONS` - Show service notifications

## Usage

### Getting Started

1. **Grant Permissions**: Allow all required permissions when prompted
2. **Start Service**: Toggle the WiFi Manager service on the main screen
3. **Save Networks**: Connect to networks and choose to save passwords
4. **Automatic Management**: Enjoy seamless WiFi switching based on signal strength

### Enhanced Features

#### Intelligent Network Scanning

- Smart scanning intervals: 8 seconds for optimal responsiveness
- Signal strength evaluation with multi-factor scoring
- Frequency band detection and preference
- Automatic filtering of weak signals (below -75 dBm)

#### Advanced Connection Logic

- **Priority Connection**: Saved networks with stronger signals get priority
- **Smart Band Switching**: Automatic preference for 5GHz over 2.4GHz
- **Connection Stability**: 15-second stability period before switching
- **Failure Prevention**: Tracks failed attempts to prevent repeated failures

#### Secure Password Management

- **AES-256 Encryption**: Military-grade encryption for stored passwords
- **Automatic Reconnection**: Saved networks connect automatically
- **User Control**: Option to save or not save passwords per network
- **Secure Storage**: Passwords stored in Android's encrypted SharedPreferences

#### Enhanced Signal Strength Indicators

- **Excellent**: -30 dBm or better (100%) - Green
- **Good**: -30 to -50 dBm (75%) - Green
- **Fair**: -50 to -60 dBm (50%) - Orange
- **Weak**: -60 to -75 dBm (25%) - Red-Orange
- **Very Weak**: Below -75 dBm (0%) - Red

#### Smart Network Selection

- **Connection Score Algorithm**:
  - Base signal strength percentage (0-100)
  - 5GHz frequency bonus (+10 points)
  - Saved password bonus (+15 points)
  - User priority multiplier (×5)
  - Weak signal penalty (-20 for < -75 dBm)

### Manual Network Management

- Tap any network card to connect manually
- Enhanced password dialog with security type display
- Option to save passwords for future automatic connections
- Visual indicators for saved networks (star icon)
- Detailed network information including frequency band

## Technical Implementation

### Enhanced WiFi API Compatibility

#### Android 10+ (API 29+)

Modern `WifiNetworkSpecifier` with password support:

```kotlin
val specifier = WifiNetworkSpecifier.Builder()
    .setSsid(network.ssid)
    .setWpa2Passphrase(password) // Enhanced with password
    .build()
```

#### Legacy Android (API < 29)

Improved `WifiConfiguration` with proper security handling:

```kotlin
val config = WifiConfiguration().apply {
    SSID = "\"${network.ssid}\""
    // Enhanced security configuration for WPA/WPA2/WEP
    when {
        capabilities.contains("WPA") -> {
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
            preSharedKey = "\"$password\""
        }
        // Additional security types...
    }
}
```

### Advanced Service Management

- **Intelligent Foreground Service**: Optimized for battery life
- **Enhanced Notifications**: Real-time connection status updates
- **Proper Lifecycle Management**: Clean startup and shutdown
- **Connection State Tracking**: Comprehensive status monitoring

### Secure Data Management

- **Encrypted Password Storage**: AES-256-GCM encryption
- **Secure Key Management**: Android MasterKeys integration
- **Fallback Security**: Graceful degradation if encryption fails
- **Privacy Protection**: Passwords never stored in plain text

## Enhanced Limitations and Considerations

### Android System Constraints

- **Location Permission**: Critical for WiFi scanning on Android 6+
- **Background Restrictions**: Optimized to work with Doze mode and battery optimization
- **Enterprise Networks**: Limited support for complex authentication schemes
- **System WiFi Settings**: Cannot override manual user network selections

### Performance Optimizations

- **Adaptive Scanning**: 8-second intervals balance responsiveness and battery life
- **Smart Connection Logic**: Prevents unnecessary network switches
- **Memory Efficiency**: Optimized data structures and coroutine usage
- **Battery Considerations**: Intelligent service management

### Security Enhancements

- **AES Encryption**: Military-grade encryption for password storage
- **Secure Network Validation**: Enhanced security type detection
- **Permission Isolation**: Minimal permission usage
- **Data Protection**: No sensitive data in logs or debug output

## Troubleshooting

### Enhanced Debugging

#### Service Issues

- **Service Not Starting**: Check permissions and WiFi state
- **Poor Performance**: Verify location services are active
- **Connection Failures**: Monitor notification for detailed status

#### Network Discovery

- **No Networks Found**: Ensure location permission granted
- **Saved Networks Not Connecting**: Check password storage and network availability
- **Frequent Switching**: Adjust signal thresholds in advanced settings

#### Password Management

- **Passwords Not Saving**: Verify app permissions and storage availability
- **Connection Failures**: Check password accuracy and network security type
- **Forgotten Networks**: Use network management to clear stored credentials

## Future Enhancements

### Planned Advanced Features

- **Machine Learning**: Predictive network switching based on usage patterns
- **Location-Based Profiles**: Different network preferences by location
- **Enterprise Network Support**: WPA2-Enterprise and certificate authentication
- **Advanced Analytics**: Detailed connection statistics and optimization suggestions
- **Mesh Network Support**: Seamless roaming between access points

### Technical Roadmap

- **WiFi 6/6E Support**: Enhanced frequency band management
- **Network Quality Metrics**: Bandwidth and latency-based switching
- **Power Management**: Advanced battery optimization algorithms
- **Cloud Sync**: Cross-device network profile synchronization

## Contributing

Contributions are welcome! Please read the contributing guidelines and submit pull requests for any improvements.

### Development Guidelines

- Follow Material Design 3 principles
- Maintain security best practices
- Include comprehensive testing
- Document API changes thoroughly

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Android Developer Documentation
- Material Design 3 Guidelines
- Jetpack Compose Community
- WiFi Management Best Practices
- Security and Encryption Standards
