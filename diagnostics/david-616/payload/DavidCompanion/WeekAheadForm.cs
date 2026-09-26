using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace DavidCompanion;

public class WeekAheadForm : Form
{
	private FlowLayoutPanel days = new FlowLayoutPanel();

	private object speech;

	public WeekAheadForm()
	{
		Text = "David - What's Coming Up?";
		StartPosition = FormStartPosition.CenterParent;
		Size = new Size(1040, 760);
		MinimumSize = new Size(920, 650);
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		Label label = Ui.Label("WEEK AHEAD", 9, bold: true, Theme.Gold);
		label.SetBounds(28, 20, 180, 24);
		Controls.Add(label);
		Label label2 = Ui.Label("What's coming up?", 25, bold: true, Theme.Text);
		label2.SetBounds(26, 48, 560, 52);
		Controls.Add(label2);
		Label label3 = Ui.Label("Today, tomorrow, and the next seven days — all from David's existing reminders and appointments.", 10, bold: false, Theme.Muted);
		label3.SetBounds(29, 100, 720, 32);
		Controls.Add(label3);
		Button button = Ui.Button("READ OVERVIEW TO ME", Theme.Blue, 9);
		button.SetBounds(760, 48, 240, 46);
		button.Anchor = AnchorStyles.Top | AnchorStyles.Right;
		Controls.Add(button);
		EventHandler value = (object param0, EventArgs param1) =>
		{
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			VoiceService.Stop(speech);
			speech = VoiceService.Speak(WeekAheadService.SpokenWeekOverview(DateTime.Now), securitySettings.VoiceName, securitySettings.VoiceRate, securitySettings.VoiceVolume);
		};
		button.Click += value;
		days.SetBounds(26, 148, 974, 520);
		days.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
		days.FlowDirection = FlowDirection.TopDown;
		days.WrapContents = false;
		days.AutoScroll = true;
		days.BackColor = Theme.Background;
		days.Padding = new Padding(0, 0, 8, 0);
		Controls.Add(days);
		Button button2 = Ui.Button("CLOSE", Color.FromArgb(78, 94, 116), 9);
		button2.SetBounds(820, 682, 180, 42);
		button2.Anchor = AnchorStyles.Bottom | AnchorStyles.Right;
		Controls.Add(button2);
		button2.Click += (object param0, EventArgs param1) =>
		{
			Close();
		};
		Shown += (object param0, EventArgs param1) =>
		{
			BuildDays();
		};
		FormClosed += (object param0, FormClosedEventArgs param1) =>
		{
			VoiceService.Stop(speech);
		};
	}

	private void BuildDays()
	{
		DateTime now = DateTime.Now;
		List<Occurrence> source = WeekAheadService.NextDays(now, 7);
		days.SuspendLayout();
		days.Controls.Clear();
		for (int i = 0; i < 7; i++)
		{
			DateTime day = now.Date.AddDays(i);
			List<Occurrence> list = source.Where((Occurrence x) => x.Due.Date == day).ToList();
			int num = Math.Max(1, list.Count);
			RoundedPanel roundedPanel = Ui.Card(Theme.Surface);
			roundedPanel.Width = 920;
			roundedPanel.Height = 58 + num * 48;
			Label label = Ui.Label(WeekAheadService.FriendlyDay(day, now), 11, bold: true, (i <= 1) ? Theme.Gold : Theme.Text);
			label.SetBounds(20, 14, 620, 28);
			roundedPanel.Controls.Add(label);
			if (list.Count == 0)
			{
				Label label2 = Ui.Label("Nothing scheduled.", 10, bold: false, Theme.Muted);
				label2.SetBounds(34, 52, 500, 26);
				roundedPanel.Controls.Add(label2);
			}
			else
			{
				int num2 = 50;
				foreach (Occurrence item in list)
				{
					string text = WeekAheadService.Status(item, now);
					Color color = text switch
					{
						"HELD" => Theme.Gold, 
						"WAITING" => Theme.Danger, 
						"DONE" => Theme.Green, 
						_ => Theme.Muted, 
					};
					Label label3 = Ui.Label(item.Due.ToString("h:mm tt"), 9, bold: true, Theme.Blue);
					label3.SetBounds(34, num2, 100, 26);
					roundedPanel.Controls.Add(label3);
					Label label4 = Ui.Label((item.Reminder == null || string.IsNullOrWhiteSpace(item.Reminder.Title)) ? "Reminder" : item.Reminder.Title, 11, bold: true, Theme.Text);
					label4.SetBounds(145, num2, 500, 27);
					roundedPanel.Controls.Add(label4);
					Label label5 = Ui.Label((item.Reminder == null || string.IsNullOrWhiteSpace(item.Reminder.Category)) ? "Other" : item.Reminder.Category, 9, bold: false, Theme.Muted);
					label5.SetBounds(655, num2, 120, 26);
					roundedPanel.Controls.Add(label5);
					Label label6 = Ui.Label(text, 8, bold: true, color);
					label6.TextAlign = ContentAlignment.MiddleRight;
					label6.SetBounds(790, num2, 95, 26);
					roundedPanel.Controls.Add(label6);
					num2 += 48;
				}
			}
			days.Controls.Add(roundedPanel);
		}
		days.ResumeLayout();
	}
}
