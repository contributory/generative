# TODO

- [ ] Extract message regeneration and deletion confirmation dialogs from **home/chat/ui/ChatScreen.kt** into a dedicated chat-dialog UI file, keeping confirmation state and callbacks in the screen unless a clean boundary supports moving them.
- [ ] Split **shared/ui/model/ModelPickerSheet.kt** into focused internal UI components and model-list/presentation helpers so the bottom sheet remains a small composition entry point.
- [ ] Move speech-service add/edit and delete dialogs out of **features/speech/ui/SpeechServicesScreen.kt** into dedicated dialog UI file(s), preserving the existing save, test, and delete behavior.
- [ ] Extract binary-test parameter, binary-upload warning, binary-test result, and OAuth-client dialogs from **features/machines/details/ui/RemoteMachineSettings.kt** into dedicated UI files.
- [ ] Review the dialog orchestration in **features/providers/list/ui/ProvidersScreen.kt** after splitting the main screen; move dialog-host wiring into a focused component only if it reduces screen responsibilities without duplicating the existing **ProviderGroupDialogs.kt** components.
