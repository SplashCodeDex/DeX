# Redesign PIN Request Card

Redesign the `PairingRequestDialog` to a more modern, "bubble"-themed aesthetic inspired by the provided UI/UX designs. The new design will move away from standard Material `OutlinedTextField` and two-button layout to a more unified, centered, and playful card.

## User Review Required

> [!IMPORTANT]
> The new design replaces the standard "Accept" and "Cancel" buttons with a single large action button at the bottom and a close (X) button at the top right.

> [!NOTE]
> I will implement the PIN entry using separate rounded boxes ("bubbles") for each digit, as seen in one of the provided images, which fits the "bubbles" request well.

## Proposed Changes

### UI Components

#### [MODIFY] [ErrorDialogs.kt](file:///W:/CodeDeX/DeX/DeX/app/src/main/java/com/example/dex/ui/components/ErrorDialogs.kt)
- Redesign `PairingRequestDialog`.
- Add a close button at the top-right.
- Change the title styling to use a serif font and centered alignment.
- Implement a custom `PinInput` component with 6 rounded "bubble" boxes.
- Replace the bottom row of buttons with a single large, pill-shaped `DeXButton`.

## Verification Plan

### Manual Verification
- Deploy the app.
- Trigger a pairing request (e.g., from a test device or by mocking a message).
- Verify the new UI appears correctly.
- Test PIN entry (valid/invalid cases).
- Test the close button.
- Verify the "Accept" button enables only when 6 digits are entered.
