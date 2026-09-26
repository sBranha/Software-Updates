using System;
using System.Drawing;
using System.Windows.Forms;

namespace DavidCompanion;

public class TodayCard : RoundedPanel
{
	public TodayCard(Occurrence o, StateEntry state, bool large, Action changed)
	{
		TodayCard todayCard = this;
		Height = (large ? 100 : 86);
		Width = 1100;
		BackColor = Theme.Surface;
		Radius = 18;
		BorderColor = Theme.Border;
		BorderWidth = 1;
		Margin = new Padding(0, 0, 0, 10);
		Color color = Theme.Category(o.Reminder.Category);
		Panel value = new Panel
		{
			BackColor = color,
			Dock = DockStyle.Left,
			Width = 7
		};
		Controls.Add(value);
		string text = AppData.ResolvePicture(o.Reminder.PicturePath);
		if (!string.IsNullOrEmpty(text))
		{
			PictureBox pictureBox = new PictureBox();
			pictureBox.SetBounds(18, 10, 62, large ? 74 : 60);
			pictureBox.SizeMode = PictureBoxSizeMode.Zoom;
			try
			{
				using Image original = Image.FromFile(text);
				pictureBox.Image = new Bitmap(original);
			}
			catch
			{
			}
			Controls.Add(pictureBox);
		}
		else
		{
			Label label = Ui.Label(Theme.CategoryBadge(o.Reminder.Category), large ? 11 : 9, bold: true, color);
			label.TextAlign = ContentAlignment.MiddleCenter;
			label.SetBounds(18, 12, 66, large ? 66 : 54);
			Controls.Add(label);
		}
		Label label2 = Ui.Label(o.Reminder.Title ?? "Reminder", large ? 15 : 12, bold: true, Theme.Text);
		label2.SetBounds(98, 10, 560, 30);
		Controls.Add(label2);
		string text2 = (string.IsNullOrWhiteSpace(o.Reminder.RoutineName) ? "" : ("  •  " + o.Reminder.RoutineName + ((o.Reminder.RoutineOrder > 0) ? (" #" + o.Reminder.RoutineOrder) : "")));
		Label label3 = Ui.Label(o.Due.ToString("h:mm tt") + "  •  " + (o.Reminder.Category ?? "Other") + text2, large ? 10 : 9, bold: false, Theme.Muted);
		label3.SetBounds(99, large ? 48 : 42, 620, 25);
		Controls.Add(label3);
		bool num = state != null && state.Status == "done";
		bool flag = state != null && (state.Status == "dismissed" || state.Status == "quiet-missed");
		bool flag2 = state != null && (state.Status == "quiet-held" || state.Status == "away-held");
		string text3 = "UPCOMING";
		Color color2 = Theme.Muted;
		if (num)
		{
			text3 = "DONE";
			color2 = Theme.Green;
		}
		else if (flag2)
		{
			text3 = "HELD";
			color2 = Theme.Blue;
		}
		else if (flag)
		{
			text3 = "MISSED";
			color2 = Theme.Gold;
		}
		else if (state != null && state.Status == "snoozed")
		{
			text3 = "SNOOZED";
			color2 = Theme.Gold;
		}
		else if (DateTime.Now > o.Due)
		{
			text3 = "DUE NOW";
			color2 = Theme.Danger;
		}
		Label label4 = Ui.Label(text3, large ? 10 : 9, bold: true, color2);
		label4.TextAlign = ContentAlignment.MiddleRight;
		label4.SetBounds(710, large ? 16 : 12, 125, 28);
		Controls.Add(label4);
		if (num)
		{
			return;
		}
		Button button = Ui.Button((DateTime.Now < o.Due) ? "I ALREADY DID THIS" : "MARK DONE", Theme.Green, 8);
		button.SetBounds(845, large ? 24 : 19, 155, 40);
		Controls.Add(button);
		button.Click += (object param0, EventArgs param1) =>
		{
			if (CompletionHelper.Confirm(todayCard.FindForm(), o.Reminder))
			{
				AppData.SetState(o.Key, "done", null);
				AppData.AddHistory(o.Reminder, (DateTime.Now < o.Due) ? "Done early" : "Done from home", o.Key, o.Due);
				if (changed != null)
				{
					changed();
				}
			}
		};
	}
}
