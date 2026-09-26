using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace DavidCompanion;

public class SystemStatusForm : Form
{
	private FlowLayoutPanel list = new FlowLayoutPanel();

	private Label title = new Label();

	public SystemStatusForm()
	{
		Text = "David - Today Status";
		StartPosition = FormStartPosition.CenterParent;
		Size = new Size(820, 700);
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		title = Ui.Label("Today & computer status", 22, bold: true, Theme.Text);
		title.SetBounds(28, 20, 620, 44);
		Controls.Add(title);
		Label label = Ui.Label("A simple view of today's reminders and whether David's computer can reach Connected Care.", 10, bold: false, Theme.Muted);
		label.SetBounds(30, 65, 720, 45);
		Controls.Add(label);
		list.SetBounds(28, 120, 744, 470);
		list.FlowDirection = FlowDirection.TopDown;
		list.WrapContents = false;
		list.AutoScroll = true;
		list.BackColor = Theme.Background;
		Controls.Add(list);
		Button button = Ui.Button("REFRESH", Theme.Blue, 9);
		button.SetBounds(28, 610, 160, 42);
		Controls.Add(button);
		EventHandler value = (object param0, EventArgs param1) =>
		{
			RefreshStatus();
		};
		button.Click += value;
		Button button2 = Ui.Button("OKAY", Theme.Green, 10);
		button2.SetBounds(590, 610, 182, 42);
		Controls.Add(button2);
		button2.Click += (object param0, EventArgs param1) =>
		{
			Close();
		};
		Shown += (object param0, EventArgs param1) =>
		{
			RefreshStatus();
		};
	}

	private void AddRow(string name, string detail, bool ok)
	{
		RoundedPanel roundedPanel = Ui.Card(ok ? Color.FromArgb(20, 55, 44) : Color.FromArgb(63, 42, 43));
		roundedPanel.Size = new Size(700, 70);
		Label label = Ui.Label((ok ? "✓  " : "!  ") + name, 11, bold: true, ok ? Theme.Green : Theme.Gold);
		label.SetBounds(16, 10, 240, 26);
		roundedPanel.Controls.Add(label);
		Label label2 = Ui.Label(detail, 9, bold: false, Theme.Text);
		label2.SetBounds(265, 10, 415, 46);
		roundedPanel.Controls.Add(label2);
		list.Controls.Add(roundedPanel);
	}

	private void RefreshStatus()
	{
		this.list.SuspendLayout();
		this.list.Controls.Clear();
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		DateTime now = DateTime.Now;
		bool flag = SystemAwareness.InternetAvailable();
		AddRow("Power", SystemAwareness.PowerSummary(), !SystemAwareness.LowBattery());
		AddRow("Internet", flag ? "Internet connection is available." : "This computer cannot see an active network connection.", flag);
		bool ok = !CloudClient.IsConnected() || (flag && SystemAwareness.LastSyncAgeMinutes(securitySettings) <= 10.0);
		AddRow("Connected Care", SystemAwareness.ConnectedCareSummary(securitySettings), ok);
		double num = SystemAwareness.IdleMinutes();
		AddRow("Computer activity", (num < 1.0) ? "Keyboard or mouse activity within the last minute." : ("Last keyboard/mouse activity about " + SystemAwareness.FriendlyAge(num) + " ago."), ok: true);
		List<Occurrence> list = Scheduler.Today(now);
		StateStore stateStore = AppData.LoadState();
		int num2 = 0;
		int num3 = 0;
		int num4 = 0;
		foreach (Occurrence o in list)
		{
			List<StateEntry> items = stateStore.Items;
			Func<StateEntry, bool> predicate = (StateEntry x) => x.Key == o.Key;
			StateEntry stateEntry = items.FirstOrDefault(predicate);
			bool flag2 = stateEntry != null && string.Equals(stateEntry.Status, "done", StringComparison.OrdinalIgnoreCase);
			if (flag2)
			{
				num2++;
			}
			if (string.Equals(o.Reminder.Category, "Medication", StringComparison.OrdinalIgnoreCase))
			{
				num3++;
				if (flag2)
				{
					num4++;
				}
			}
		}
		int count = DailySummary.GetMissedWork(now).Count;
		string detail = ((list.Count == 0) ? "Nothing scheduled today." : (num2 + " of " + list.Count + " reminders completed" + ((count > 0) ? (" • " + count + " missed/waiting") : "") + ((num3 > 0) ? (" • medication " + num4 + "/" + num3) : "")));
		AddRow("Today", detail, count == 0);
		string text = "Not scheduled";
		if (string.Equals(securitySettings.CheckInEnabled, "on", StringComparison.OrdinalIgnoreCase))
		{
			StateEntry stateEntry2 = AppData.GetState(DailySupport.CheckInStateKey(now));
			if (stateEntry2 != null)
			{
				text = (string.Equals(stateEntry2.Status, "done", StringComparison.OrdinalIgnoreCase) ? "I'M OKAY received" : (stateEntry2.Status ?? "Waiting"));
			}
			else
			{
				text = "Waiting";
			}
		}
		AddRow("I'M OKAY check-in", text, !string.Equals(text, "missed", StringComparison.OrdinalIgnoreCase));
		this.list.ResumeLayout();
	}
}
