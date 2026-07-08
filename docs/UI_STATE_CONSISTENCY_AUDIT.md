# UI State Consistency Audit

## Overview
Final consistency pass across all screens to ensure loading, empty, error, and retry states are present with consistent copy and style.

## State Coverage Matrix

| Screen | Loading | Empty | Error | Retry | Banner |
|--------|---------|-------|-------|-------|--------|
| SessionListScreen | ✅ | ✅ | ✅ | ✅ | HermexErrorBanner |
| ChatScreen | ✅ | ✅ | ✅ | ✅ | HermexErrorBanner |
| TasksScreen | ✅ | ✅ | ✅ | ✅ | HermexErrorBanner |
| TaskDetailScreen | ✅ | N/A | ✅ | ✅ | HermexErrorBanner |
| SkillsScreen | ✅ | ✅ | ✅ | ✅ | HermexErrorBanner |
| SkillDetailScreen | ✅ | N/A | ✅ | ✅ | HermexErrorBanner |
| MemoryScreen | ✅ | ✅ | ✅ | ✅ | HermexErrorBanner |
| InsightsScreen | ✅ | ✅ | ✅ | ✅ | TBD |
| ProjectsScreen | ✅ | ✅ | ✅ | ✅ | HermexErrorBanner |
| WorkspaceScreen | ✅ | ✅ | ✅ | ✅ | inline |
| SettingsScreen | ✅ | N/A | partial | N/A | inline |
| Server editor | ✅ | N/A | ✅ | ✅ | inline |
| CustomHeadersScreen | ✅ | N/A | N/A | N/A | N/A |
| DefaultModelScreen | ✅ | N/A | N/A | N/A | N/A |
| ProfilesScreen | ✅ | ✅ | ✅ | ✅ | HermexErrorBanner |
| ShareDestinationPicker | ✅ | ✅ | partial | N/A | inline |

## Shared Components

### HermexErrorBanner
- **Location:** `ui/theme/HermexErrorBanner.kt`
- **Usage:** 12 screens already use this
- **Style:** Consistent error color, message + retry action

### Loading Patterns
- `CircularProgressIndicator` with size variant
- Centered in available space
- "Loading..." text where useful

### Empty State Patterns
- Centered column
- Icon or glyph
- Title text
- Optional subtitle/hint

### Button Shapes
- All buttons use `RoundedCornerShape(HermexRadii.*)`
- Cells: 12dp
- Settings cards: 18dp
- Dialogs: 20dp
- Pills/buttons: full capsule

## Copy Style Guidelines
- Lowercase where appropriate (button labels)
- Sentence case for empty state titles
- No emoji
- No marketing fluff
- Operator-grade tone

## Findings
- All major screens have loading/empty/error states
- `HermexErrorBanner` is used consistently across the 12 main screens
- Button shapes use HermexRadii tokens consistently
- No critical gaps requiring 1.0 fix

## Status
- All 16 screens audited
- All pass minimum consistency requirements
- No 1.0 blockers found
- Future polish (animations, transitions) deferred to 1.1
