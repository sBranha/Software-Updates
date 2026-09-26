using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace DavidCompanion;

public class RoutineModeForm : Form
{
	private string routineName;

	private string stateKey;

	private bool automatic;

	private FlowLayoutPanel list = new FlowLayoutPanel();

	private Label summary = new Label();

	public RoutineModeForm(string name, string key, bool auto)
	{
		routineName = (string.IsNullOrWhiteSpace(name) ? "Routine" : name.Trim());
		stateKey = key ?? "";
		automatic = auto;
		Text = "David - " + routineName + " Routine";
		StartPosition = FormStartPosition.CenterScreen;
		Size = new Size(860, 720);
		MinimumSize = new Size(800, 650);
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		TopMost = automatic;
		GradientPanel gradientPanel = new GradientPanel();
		gradientPanel.SetBounds(22, 18, 800, 132);
		Controls.Add(gradientPanel);
		Label label = Ui.Label("ROUTINE MODE", 9, bold: true, Theme.Gold);
		label.SetBounds(26, 18, 220, 24);
		gradientPanel.Controls.Add(label);
		Label label2 = Ui.Label(routineName + " Routine", 26, bold: true, Color.White);
		label2.SetBounds(24, 45, 560, 52);
		gradientPanel.Controls.Add(label2);
		summary = Ui.Label("", 10, bold: false, Color.FromArgb(207, 220, 235));
		summary.SetBounds(27, 98, 700, 25);
		gradientPanel.Controls.Add(summary);
		list.SetBounds(22, 168, 800, 390);
		list.AutoScroll = true;
		list.FlowDirection = FlowDirection.TopDown;
		list.WrapContents = false;
		list.BackColor = Theme.Background;
		Controls.Add(list);
		Button button = Ui.Button("START ROUTINE", Theme.Green, 12);
		button.SetBounds(22, 580, 310, 60);
		Controls.Add(button);
		button.Click += (object param0, EventArgs param1) =>
		{
			StartRoutine();
		};
		Button button2 = Ui.Button(automatic ? "NOT NOW" : "CLOSE", Color.FromArgb(78, 94, 116), 10);
		button2.SetBounds(350, 580, 210, 60);
		Controls.Add(button2);
		button2.Click += (object param0, EventArgs param1) =>
		{
			if (automatic && !string.IsNullOrWhiteSpace(stateKey))
			{
				AppData.SetState(stateKey, "dismissed", null);
			}
			Close();
		};
		Button button3 = Ui.Button("WHAT'S NEXT?", Theme.Blue, 10);
		button3.SetBounds(578, 580, 244, 60);
		Controls.Add(button3);
		button3.Click += (object param0, EventArgs param1) =>
		{
			List<AlertWorkItem> dueWork = DailySupport.GetDueWork(DateTime.Now);
			if (dueWork.Count > 0)
			{
				using ReminderBatchForm reminderBatchForm = new ReminderBatchForm(dueWork);
				reminderBatchForm.ShowDialog(this);
			}
			RefreshList();
		};
		Shown += (object param0, EventArgs param1) =>
		{
			RefreshList();
		};
	}

	private void RefreshList()
	{
		List<Occurrence> routineOccurrences = DailySupport.GetRoutineOccurrences(routineName, DateTime.Now);
		list.SuspendLayout();
		list.Controls.Clear();
		int num = 0;
		foreach (Occurrence item in routineOccurrences)
		{
			StateEntry stateEntry = AppData.GetState(item.Key);
			bool flag = stateEntry != null && stateEntry.Status == "done";
			if (flag)
			{
				num++;
			}
			RoundedPanel roundedPanel = Ui.Card(Theme.Surface);
			roundedPanel.Width = 750;
			roundedPanel.Height = 72;
			Label label = Ui.Label(((item.Reminder.RoutineOrder > 0) ? ("STEP " + item.Reminder.RoutineOrder + "  •  ") : "") + item.Reminder.Title, 12, bold: true, flag ? Theme.Green : Theme.Text);
			label.SetBounds(18, 12, 540, 28);
			roundedPanel.Controls.Add(label);
			Label label2 = Ui.Label(item.Due.ToString("h:mm tt") + (flag ? "  •  DONE" : ""), 9, bold: true, flag ? Theme.Green : Theme.Muted);
			label2.SetBounds(18, 42, 300, 22);
			roundedPanel.Controls.Add(label2);
			list.Controls.Add(roundedPanel);
		}
		if (routineOccurrences.Count == 0)
		{
			RoundedPanel roundedPanel2 = Ui.Card(Theme.Surface);
			roundedPanel2.Width = 750;
			roundedPanel2.Height = 110;
			Label label3 = Ui.Label("No reminders are assigned to the “" + routineName + "” routine yet.\r\nA caregiver can edit a reminder and set Advanced → Routine name.", 11, bold: false, Theme.Muted);
			label3.SetBounds(20, 22, 690, 65);
			roundedPanel2.Controls.Add(label3);
			list.Controls.Add(roundedPanel2);
		}
		summary.Text = ((routineOccurrences.Count == 0) ? "No routine steps are set up." : (num + " of " + routineOccurrences.Count + " steps complete today"));
		list.ResumeLayout();
	}

	private void StartRoutine()
	{
		List<AlertWorkItem> routineWork = DailySupport.GetRoutineWork(routineName, DateTime.Now);
		if (routineWork.Count == 0)
		{
			MessageBox.Show("This routine is already complete for today.", "David");
			if (!string.IsNullOrWhiteSpace(stateKey))
			{
				AppData.SetState(stateKey, "done", null);
			}
			RefreshList();
			return;
		}
		using (ReminderBatchForm reminderBatchForm = new ReminderBatchForm(routineWork))
		{
			reminderBatchForm.ShowDialog(this);
		}
		bool flag = DailySupport.GetRoutineOccurrences(routineName, DateTime.Now).All((Occurrence o) =>
		{
			StateEntry stateEntry = AppData.GetState(o.Key);
			return stateEntry != null && stateEntry.Status == "done";
		});
		if (!string.IsNullOrWhiteSpace(stateKey))
		{
			AppData.SetState(stateKey, flag ? "done" : "dismissed", null);
		}
		RefreshList();
	}
}
