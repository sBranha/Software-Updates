using System;
using System.Collections.Generic;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace DavidCompanion;

public class DailyReportForm : Form
{
	public DailyReportForm()
	{
		Text = "David - Today's Caregiver Report";
		StartPosition = FormStartPosition.CenterParent;
		Size = new Size(940, 610);
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		DateTime now = DateTime.Now;
		List<Occurrence> list = Scheduler.Today(now);
		StateStore stateStore = AppData.LoadState();
		int num = 0;
		int num2 = 0;
		int num3 = 0;
		foreach (Occurrence o in list)
		{
			List<StateEntry> items = stateStore.Items;
			Func<StateEntry, bool> predicate = (StateEntry x) => x.Key == o.Key;
			StateEntry stateEntry = items.FirstOrDefault(predicate);
			if (stateEntry != null && stateEntry.Status == "done")
			{
				num++;
			}
			else if (o.Due < now)
			{
				num2++;
			}
			else
			{
				num3++;
			}
		}
		Label label = Ui.Label("Today's Caregiver Report", 20, bold: true, Theme.Text);
		label.SetBounds(24, 18, 500, 42);
		Controls.Add(label);
		Label label2 = Ui.Label("Completed: " + num + "     Overdue / missed: " + num2 + "     Upcoming: " + num3, 12, bold: true, (num2 > 0) ? Theme.Danger : Theme.Green);
		label2.SetBounds(26, 64, 850, 34);
		Controls.Add(label2);
		DataGridView dataGridView = new DataGridView();
		dataGridView.SetBounds(24, 112, 880, 430);
		dataGridView.ReadOnly = true;
		dataGridView.AllowUserToAddRows = false;
		dataGridView.AutoSizeColumnsMode = DataGridViewAutoSizeColumnsMode.Fill;
		dataGridView.BackgroundColor = Theme.Surface;
		dataGridView.BorderStyle = BorderStyle.None;
		dataGridView.RowHeadersVisible = false;
		Controls.Add(dataGridView);
		DataTable dataTable = new DataTable
		{
			Columns = { "Time", "Reminder", "Routine", "Status" }
		};
		foreach (Occurrence o2 in list)
		{
			List<StateEntry> items2 = stateStore.Items;
			Func<StateEntry, bool> predicate2 = (StateEntry x) => x.Key == o2.Key;
			StateEntry stateEntry2 = items2.FirstOrDefault(predicate2);
			string text = ((stateEntry2 != null && stateEntry2.Status == "done") ? "Completed" : ((stateEntry2 != null && stateEntry2.Status == "snoozed") ? "Snoozed" : ((o2.Due < now) ? "OVERDUE / MISSED" : "Upcoming")));
			dataTable.Rows.Add(o2.Due.ToString("h:mm tt"), o2.Reminder.Title, o2.Reminder.RoutineName, text);
		}
		dataGridView.DataSource = dataTable;
	}
}
