import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

@main
struct OdakWidgetsBundle: WidgetBundle {
    var body: some Widget {
        FocusLiveActivity()
        if #available(iOS 18.0, *) {
            StartFocusControl()
        }
    }
}

// MARK: - Kilit ekranı + Dynamic Island

private let odakGreen = Color(red: 0.40, green: 0.80, blue: 0.45)

struct FocusLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: FocusActivityAttributes.self) { context in
            let range = context.state.startAt...max(context.state.endAt, context.state.startAt.addingTimeInterval(1))
            HStack(spacing: 14) {
                Text(context.state.isBreak ? "☕" : context.attributes.plantEmoji)
                    .font(.system(size: 40))
                VStack(alignment: .leading, spacing: 6) {
                    Text(context.state.isBreak ? "Mola zamanı" : "Odak · \(context.attributes.subject)")
                        .font(.headline)
                        .foregroundColor(.white)
                    Text(context.state.isBreak ? "Biraz dinlen 🧘" : "\(context.attributes.plantName) büyüyor 🌱")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.75))
                    ProgressView(timerInterval: range, countsDown: false) {
                        EmptyView()
                    } currentValueLabel: {
                        EmptyView()
                    }
                    .tint(odakGreen)
                }
                Spacer(minLength: 4)
                Text(timerInterval: range, countsDown: true)
                    .font(.system(size: 30, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .foregroundColor(.white)
                    .multilineTextAlignment(.trailing)
                    .frame(width: 96, alignment: .trailing)
            }
            .padding(16)
            .activityBackgroundTint(Color.black.opacity(0.8))
            .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            let range = context.state.startAt...max(context.state.endAt, context.state.startAt.addingTimeInterval(1))
            let emoji = context.state.isBreak ? "☕" : context.attributes.plantEmoji
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Text(emoji).font(.system(size: 38))
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(timerInterval: range, countsDown: true)
                        .font(.system(size: 26, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .multilineTextAlignment(.trailing)
                        .frame(width: 90, alignment: .trailing)
                }
                DynamicIslandExpandedRegion(.center) {
                    Text(context.state.isBreak ? "Mola" : context.attributes.subject)
                        .font(.headline)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    ProgressView(timerInterval: range, countsDown: false) {
                        EmptyView()
                    } currentValueLabel: {
                        EmptyView()
                    }
                    .tint(odakGreen)
                    .padding(.horizontal, 8)
                }
            } compactLeading: {
                Text(emoji)
            } compactTrailing: {
                Text(timerInterval: range, countsDown: true)
                    .font(.caption.bold())
                    .monospacedDigit()
                    .multilineTextAlignment(.trailing)
                    .frame(width: 46)
                    .foregroundColor(odakGreen)
            } minimal: {
                Text(emoji)
            }
            .keylineTint(odakGreen)
        }
    }
}

// MARK: - Denetim Merkezi düğmesi (iOS 18+)

@available(iOS 18.0, *)
struct StartFocusControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "com.tiyzi.odakbahcesi.startfocus") {
            ControlWidgetButton(action: StartFocusIntent()) {
                Label("Odaklan", systemImage: "leaf.fill")
            }
        }
        .displayName("Odak Başlat")
        .description("Odak Bahçesi'nde yeni bir odak seansı başlatır.")
    }
}
