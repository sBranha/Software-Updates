using System;
using System.Drawing;
using System.Globalization;
using System.Windows.Forms;

namespace DavidCompanion;

public class EditReminderForm : Form
{
	private TextBox titleBox = new TextBox();

	private TextBox messageBox = new TextBox();

	private ComboBox category = new ComboBox();

	private ComboBox schedule = new ComboBox();

	private DateTimePicker timePicker = new DateTimePicker();

	private DateTimePicker endTimePicker = new DateTimePicker();

	private ComboBox weeklyDay = new ComboBox();

	private DateTimePicker onceDate = new DateTimePicker();

	private NumericUpDown snooze = new NumericUpDown();

	private CheckBox enabled = new CheckBox();

	private CheckBox voice = new CheckBox();

	private CheckBox repeat = new CheckBox();

	private ComboBox importance = new ComboBox();

	private TextBox spoken = new TextBox();

	private TextBox note = new TextBox();

	private TextBox picture = new TextBox();

	private PictureBox preview = new PictureBox();

	private TextBox routine = new TextBox();

	private NumericUpDown routineOrder = new NumericUpDown();

	private CheckBox confirm = new CheckBox();

	private TextBox skipDates = new TextBox();

	private TextBox overrides = new TextBox();

	private TextBox medicationDose = new TextBox();

	private TextBox medicationInstructions = new TextBox();

	private TextBox appointmentWhere = new TextBox();

	private NumericUpDown appointmentPrepare = new NumericUpDown();

	private NumericUpDown appointmentLeave = new NumericUpDown();

	private Reminder existing;

	public Reminder Value { get; private set; }

	public EditReminderForm(Reminder item)
	{
		existing = item;
		Text = ((item == null) ? "David - Add Reminder" : "David - Edit Reminder");
		StartPosition = FormStartPosition.CenterParent;
		Size = new Size(820, 830);
		FormBorderStyle = FormBorderStyle.FixedDialog;
		MaximizeBox = false;
		MinimizeBox = false;
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		Label label = Ui.Label((item == null) ? "Add a reminder" : "Edit reminder", 20, bold: true, Theme.Text);
		label.SetBounds(25, 16, 700, 42);
		Controls.Add(label);
		Label label2 = Ui.Label("Keep David's wording short and clear. Advanced options add pictures, routines, exceptions, and importance.", 9, bold: false, Theme.Muted);
		label2.SetBounds(28, 58, 720, 26);
		Controls.Add(label2);
		TabControl tabControl = new TabControl();
		tabControl.SetBounds(22, 94, 760, 625);
		Controls.Add(tabControl);
		TabPage tabPage = new TabPage("Reminder");
		TabPage tabPage2 = new TabPage("Advanced");
		TabPage tabPage3 = new TabPage("Medication / Appointment");
		tabControl.TabPages.Add(tabPage);
		tabControl.TabPages.Add(tabPage2);
		tabControl.TabPages.Add(tabPage3);
		tabPage.BackColor = Theme.Background;
		tabPage2.BackColor = Theme.Background;
		tabPage3.BackColor = Theme.Background;
		int num = 22;
		AddLabel(tabPage, "Reminder name", num);
		titleBox.SetBounds(180, num, 520, 30);
		tabPage.Controls.Add(titleBox);
		num += 46;
		AddLabel(tabPage, "Message", num);
		messageBox.SetBounds(180, num, 520, 70);
		messageBox.Multiline = true;
		tabPage.Controls.Add(messageBox);
		num += 84;
		AddLabel(tabPage, "Category", num);
		category.SetBounds(180, num, 250, 30);
		category.DropDownStyle = ComboBoxStyle.DropDownList;
		category.Items.AddRange(new object[12]
		{
			"General", "Movement / Activity", "Medication", "Appointment", "Important", "Critical", "Hygiene", "Pets", "Meals", "Chore",
			"Bedtime", "Other"
		});
		tabPage.Controls.Add(category);
		num += 43;
		AddLabel(tabPage, "Schedule", num);
		schedule.SetBounds(180, num, 250, 30);
		schedule.DropDownStyle = ComboBoxStyle.DropDownList;
		schedule.Items.AddRange(new object[11]
		{
			"Daily", "Weekdays", "Weekly", "Every hour", "Every 2 hours", "Every 3 hours", "Every 4 hours", "Every 6 hours", "Every 8 hours", "Every 12 hours",
			"Once"
		});
		tabPage.Controls.Add(schedule);
		num += 43;
		AddLabel(tabPage, "Start time", num);
		timePicker.SetBounds(180, num, 190, 30);
		timePicker.Format = DateTimePickerFormat.Custom;
		timePicker.CustomFormat = "h:mm tt";
		timePicker.ShowUpDown = true;
		tabPage.Controls.Add(timePicker);
		num += 43;
		AddLabel(tabPage, "Stop time (hourly)", num);
		endTimePicker.SetBounds(180, num, 190, 30);
		endTimePicker.Format = DateTimePickerFormat.Custom;
		endTimePicker.CustomFormat = "h:mm tt";
		endTimePicker.ShowUpDown = true;
		endTimePicker.Value = DateTime.Today.AddHours(20.0);
		tabPage.Controls.Add(endTimePicker);
		num += 43;
		AddLabel(tabPage, "Weekly day", num);
		weeklyDay.SetBounds(180, num, 205, 30);
		weeklyDay.DropDownStyle = ComboBoxStyle.DropDownList;
		ComboBox.ObjectCollection items = weeklyDay.Items;
		object[] names = Enum.GetNames(typeof(DayOfWeek));
		object[] items2 = names;
		items.AddRange(items2);
		tabPage.Controls.Add(weeklyDay);
		num += 43;
		AddLabel(tabPage, "One-time date", num);
		onceDate.SetBounds(180, num, 205, 30);
		onceDate.Format = DateTimePickerFormat.Short;
		tabPage.Controls.Add(onceDate);
		num += 43;
		AddLabel(tabPage, "Snooze", num);
		snooze.SetBounds(180, num, 95, 30);
		snooze.Minimum = 1m;
		snooze.Maximum = 120m;
		snooze.Value = 10m;
		tabPage.Controls.Add(snooze);
		Label label3 = Ui.Label("minutes", 9, bold: false, Theme.Muted);
		label3.SetBounds(285, num + 5, 100, 24);
		tabPage.Controls.Add(label3);
		num += 42;
		enabled.Text = "Reminder enabled";
		enabled.ForeColor = Theme.Text;
		enabled.SetBounds(180, num, 220, 28);
		enabled.Checked = true;
		tabPage.Controls.Add(enabled);
		num += 32;
		voice.Text = "Speak the reminder out loud";
		voice.ForeColor = Theme.Text;
		voice.SetBounds(180, num, 290, 28);
		voice.Checked = true;
		tabPage.Controls.Add(voice);
		num += 32;
		repeat.Text = "Keep reminding if closed without Done";
		repeat.ForeColor = Theme.Text;
		repeat.SetBounds(180, num, 340, 28);
		repeat.Checked = true;
		tabPage.Controls.Add(repeat);
		num = 20;
		AddLabel(tabPage2, "Importance", num);
		importance.SetBounds(180, num, 220, 30);
		importance.DropDownStyle = ComboBoxStyle.DropDownList;
		importance.Items.AddRange(new object[3] { "Normal", "Important", "Critical" });
		tabPage2.Controls.Add(importance);
		num += 44;
		AddLabel(tabPage2, "Spoken words", num);
		spoken.SetBounds(180, num, 520, 62);
		spoken.Multiline = true;
		tabPage2.Controls.Add(spoken);
		num += 76;
		Label label4 = Ui.Label("Optional. If filled in, David speaks these exact words instead of the automatic sentence.", 8, bold: false, Theme.Muted);
		label4.SetBounds(181, num - 10, 520, 22);
		tabPage2.Controls.Add(label4);
		num += 20;
		AddLabel(tabPage2, "Instructions", num);
		note.SetBounds(180, num, 520, 58);
		note.Multiline = true;
		tabPage2.Controls.Add(note);
		num += 72;
		AddLabel(tabPage2, "Picture", num);
		picture.SetBounds(180, num, 350, 30);
		picture.ReadOnly = true;
		tabPage2.Controls.Add(picture);
		Button button = Ui.Button("CHOOSE", Color.FromArgb(73, 91, 117), 8);
		button.SetBounds(540, num, 90, 30);
		tabPage2.Controls.Add(button);
		Button button2 = Ui.Button("CLEAR", Color.FromArgb(100, 83, 90), 8);
		button2.SetBounds(636, num, 64, 30);
		tabPage2.Controls.Add(button2);
		preview.SetBounds(88, num - 5, 72, 60);
		preview.SizeMode = PictureBoxSizeMode.Zoom;
		tabPage2.Controls.Add(preview);
		num += 70;
		AddLabel(tabPage2, "Routine name", num);
		routine.SetBounds(180, num, 260, 30);
		tabPage2.Controls.Add(routine);
		Label label5 = Ui.Label("Step", 9, bold: true, Theme.Text);
		label5.SetBounds(455, num + 5, 45, 22);
		tabPage2.Controls.Add(label5);
		routineOrder.SetBounds(500, num, 70, 30);
		routineOrder.Minimum = 0m;
		routineOrder.Maximum = 50m;
		tabPage2.Controls.Add(routineOrder);
		num += 44;
		confirm.Text = "Ask for a second confirmation before marking Done";
		confirm.ForeColor = Theme.Text;
		confirm.SetBounds(180, num, 430, 28);
		tabPage2.Controls.Add(confirm);
		num += 40;
		AddLabel(tabPage2, "Skip dates", num);
		skipDates.SetBounds(180, num, 520, 30);
		tabPage2.Controls.Add(skipDates);
		num += 36;
		Label label6 = Ui.Label("Example: 2026-09-05, 2026-09-12", 8, bold: false, Theme.Muted);
		label6.SetBounds(181, num, 400, 20);
		tabPage2.Controls.Add(label6);
		num += 25;
		AddLabel(tabPage2, "Date/time changes", num);
		overrides.SetBounds(180, num, 520, 52);
		overrides.Multiline = true;
		tabPage2.Controls.Add(overrides);
		num += 58;
		Label label7 = Ui.Label("Example: 2026-09-10=14:30; 2026-09-11=09:15", 8, bold: false, Theme.Muted);
		label7.SetBounds(181, num, 480, 20);
		tabPage2.Controls.Add(label7);
		Label label8 = Ui.Label("MEDICATION DETAILS", 12, bold: true, Theme.Green);
		label8.SetBounds(24, 24, 300, 28);
		tabPage3.Controls.Add(label8);
		Label label9 = Ui.Label("Used only when Category is Medication. Enter exactly what David should see from the medicine label/caregiver instructions.", 9, bold: false, Theme.Muted);
		label9.SetBounds(26, 55, 675, 45);
		tabPage3.Controls.Add(label9);
		AddLabel(tabPage3, "Dose", 112);
		medicationDose.SetBounds(180, 112, 520, 30);
		tabPage3.Controls.Add(medicationDose);
		AddLabel(tabPage3, "Medicine instructions", 158);
		medicationInstructions.SetBounds(180, 158, 520, 72);
		medicationInstructions.Multiline = true;
		tabPage3.Controls.Add(medicationInstructions);
		Label label10 = Ui.Label("APPOINTMENT COUNTDOWN", 12, bold: true, Theme.Blue);
		label10.SetBounds(24, 270, 330, 28);
		tabPage3.Controls.Add(label10);
		Label label11 = Ui.Label("Used only when Category is Appointment. David can remind him the day before, when to get ready, and when it is time to leave.", 9, bold: false, Theme.Muted);
		label11.SetBounds(26, 300, 675, 45);
		tabPage3.Controls.Add(label11);
		AddLabel(tabPage3, "Where", 360);
		appointmentWhere.SetBounds(180, 360, 520, 30);
		tabPage3.Controls.Add(appointmentWhere);
		AddLabel(tabPage3, "Get ready", 406);
		appointmentPrepare.SetBounds(180, 406, 90, 30);
		appointmentPrepare.Minimum = 0m;
		appointmentPrepare.Maximum = 720m;
		appointmentPrepare.Value = 60m;
		tabPage3.Controls.Add(appointmentPrepare);
		Label label12 = Ui.Label("minutes before", 9, bold: false, Theme.Muted);
		label12.SetBounds(280, 411, 150, 24);
		tabPage3.Controls.Add(label12);
		AddLabel(tabPage3, "Leave", 452);
		appointmentLeave.SetBounds(180, 452, 90, 30);
		appointmentLeave.Minimum = 0m;
		appointmentLeave.Maximum = 720m;
		appointmentLeave.Value = 30m;
		tabPage3.Controls.Add(appointmentLeave);
		Label label13 = Ui.Label("minutes before", 9, bold: false, Theme.Muted);
		label13.SetBounds(280, 457, 150, 24);
		tabPage3.Controls.Add(label13);
		Label label14 = Ui.Label("David never estimates travel time. The caregiver chooses the get-ready and leave times.", 9, bold: true, Theme.Muted);
		label14.SetBounds(180, 505, 520, 45);
		tabPage3.Controls.Add(label14);
		button.Click += (object param0, EventArgs param1) =>
		{
			OpenFileDialog openFileDialog = new OpenFileDialog
			{
				Filter = "Pictures|*.jpg;*.jpeg;*.png;*.bmp;*.gif|All files|*.*"
			};
			if (openFileDialog.ShowDialog(this) == DialogResult.OK)
			{
				try
				{
					picture.Text = AppData.ImportPicture(openFileDialog.FileName);
					LoadPreview();
				}
				catch (Exception ex)
				{
					MessageBox.Show("Picture could not be added.\r\n" + ex.Message, "David");
				}
			}
		};
		button2.Click += (object param0, EventArgs param1) =>
		{
			picture.Text = "";
			if (preview.Image != null)
			{
				preview.Image.Dispose();
				preview.Image = null;
			}
		};
		Button button3 = Ui.Button("SAVE REMINDER", Theme.Green, 11);
		button3.SetBounds(392, 735, 190, 48);
		Controls.Add(button3);
		Button button4 = Ui.Button("CANCEL", Color.FromArgb(88, 103, 123), 10);
		button4.SetBounds(594, 735, 188, 48);
		Controls.Add(button4);
		button4.Click += (object param0, EventArgs param1) =>
		{
			Close();
		};
		button3.Click += SaveClicked;
		if (item != null)
		{
			LoadItem(item);
		}
		else
		{
			category.SelectedIndex = 0;
			schedule.SelectedIndex = 0;
			weeklyDay.SelectedIndex = 1;
			importance.SelectedIndex = 0;
		}
		schedule.SelectedIndexChanged += (object param0, EventArgs param1) =>
		{
			UpdateScheduleControls();
		};
		UpdateScheduleControls();
	}

	private void AddLabel(Control parent, string text, int y)
	{
		Label label = Ui.Label(text, 9, bold: true, Theme.Text);
		label.SetBounds(20, y + 4, 145, 26);
		parent.Controls.Add(label);
	}

	private static bool IsIntervalSchedule(string value)
	{
		return Scheduler.IsIntervalSchedule(value);
	}

	private static int IntervalHoursFor(string value)
	{
		Reminder r = new Reminder
		{
			Schedule = value
		};
		if (!Scheduler.IsIntervalSchedule(value))
		{
			return 0;
		}
		return Scheduler.IntervalHours(r);
	}

	private void UpdateScheduleControls()
	{
		string text = ((schedule.SelectedItem == null) ? "" : schedule.SelectedItem.ToString());
		weeklyDay.Enabled = text == "Weekly";
		onceDate.Enabled = text == "Once";
		endTimePicker.Enabled = IsIntervalSchedule(text);
	}

	private void LoadItem(Reminder r)
	{
		titleBox.Text = r.Title ?? "";
		messageBox.Text = r.Message ?? "";
		SelectCombo(category, r.Category, 0);
		SelectCombo(schedule, r.Schedule, 0);
		SelectCombo(weeklyDay, r.WeeklyDay, 1);
		if (TimeSpan.TryParse(r.Time, out var result))
		{
			timePicker.Value = DateTime.Today.Add(result);
		}
		if (TimeSpan.TryParse(r.EndTime, out var result2))
		{
			endTimePicker.Value = DateTime.Today.Add(result2);
		}
		if (DateTime.TryParseExact(r.OnceDate ?? "", "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var result3))
		{
			onceDate.Value = result3;
		}
		snooze.Value = Math.Min(snooze.Maximum, Math.Max(snooze.Minimum, (r.SnoozeMinutes <= 0) ? 10 : r.SnoozeMinutes));
		enabled.Checked = r.Enabled;
		voice.Checked = r.Voice;
		repeat.Checked = r.RepeatUntilDone;
		SelectCombo(importance, string.IsNullOrWhiteSpace(r.Importance) ? "Normal" : r.Importance, 0);
		spoken.Text = r.SpokenText ?? "";
		note.Text = r.CaregiverNote ?? "";
		picture.Text = r.PicturePath ?? "";
		routine.Text = r.RoutineName ?? "";
		routineOrder.Value = Math.Max(routineOrder.Minimum, Math.Min(routineOrder.Maximum, r.RoutineOrder));
		confirm.Checked = r.ConfirmDone;
		skipDates.Text = r.SkipDates ?? "";
		overrides.Text = r.DateOverrides ?? "";
		medicationDose.Text = r.MedicationDose ?? "";
		medicationInstructions.Text = r.MedicationInstructions ?? "";
		appointmentWhere.Text = r.AppointmentWhere ?? "";
		appointmentPrepare.Value = Math.Max(appointmentPrepare.Minimum, Math.Min(appointmentPrepare.Maximum, (r.AppointmentPrepareMinutes <= 0) ? 60 : r.AppointmentPrepareMinutes));
		appointmentLeave.Value = Math.Max(appointmentLeave.Minimum, Math.Min(appointmentLeave.Maximum, (r.AppointmentLeaveMinutes <= 0) ? 30 : r.AppointmentLeaveMinutes));
		LoadPreview();
	}

	private void LoadPreview()
	{
		try
		{
			if (preview.Image != null)
			{
				preview.Image.Dispose();
				preview.Image = null;
			}
			string text = AppData.ResolvePicture(picture.Text);
			if (!string.IsNullOrEmpty(text))
			{
				using (Image original = Image.FromFile(text))
				{
					preview.Image = new Bitmap(original);
					return;
				}
			}
		}
		catch
		{
		}
	}

	private void SelectCombo(ComboBox box, string value, int fallback)
	{
		int num = box.Items.IndexOf(value);
		box.SelectedIndex = ((num >= 0) ? num : fallback);
	}

	private void SaveClicked(object sender, EventArgs e)
	{
		if (string.IsNullOrWhiteSpace(titleBox.Text))
		{
			MessageBox.Show("Please enter a reminder name.", "David");
			return;
		}
		if (string.Equals(Convert.ToString(category.SelectedItem), "Appointment", StringComparison.OrdinalIgnoreCase) && appointmentPrepare.Value > 0m && appointmentLeave.Value > 0m && appointmentPrepare.Value <= appointmentLeave.Value)
		{
			MessageBox.Show("For an appointment, Get ready should be farther before the appointment than Leave. Example: Get ready 60 minutes before and Leave 30 minutes before.", "David");
			return;
		}
		string value = Convert.ToString(schedule.SelectedItem);
		if (IsIntervalSchedule(value) && endTimePicker.Value.TimeOfDay < timePicker.Value.TimeOfDay)
		{
			MessageBox.Show("For an hourly reminder, Stop time must be the same as or later than Start time. David will not silently continue an hourly task overnight.", "David");
			return;
		}
		Value = new Reminder
		{
			Id = ((existing == null) ? Guid.NewGuid().ToString() : existing.Id),
			Title = titleBox.Text.Trim(),
			Message = messageBox.Text.Trim(),
			Category = Convert.ToString(category.SelectedItem),
			Schedule = value,
			Time = timePicker.Value.ToString("HH:mm"),
			EndTime = (IsIntervalSchedule(value) ? endTimePicker.Value.ToString("HH:mm") : ""),
			IntervalHours = IntervalHoursFor(value),
			WeeklyDay = Convert.ToString(weeklyDay.SelectedItem),
			OnceDate = onceDate.Value.ToString("yyyy-MM-dd"),
			SnoozeMinutes = (int)snooze.Value,
			Enabled = enabled.Checked,
			Voice = voice.Checked,
			RepeatUntilDone = repeat.Checked,
			Importance = Convert.ToString(importance.SelectedItem),
			SpokenText = spoken.Text.Trim(),
			CaregiverNote = note.Text.Trim(),
			PicturePath = picture.Text.Trim(),
			RoutineName = routine.Text.Trim(),
			RoutineOrder = (int)routineOrder.Value,
			ConfirmDone = confirm.Checked,
			SkipDates = skipDates.Text.Trim(),
			DateOverrides = overrides.Text.Trim(),
			MedicationDose = medicationDose.Text.Trim(),
			MedicationInstructions = medicationInstructions.Text.Trim(),
			AppointmentWhere = appointmentWhere.Text.Trim(),
			AppointmentPrepareMinutes = (int)appointmentPrepare.Value,
			AppointmentLeaveMinutes = (int)appointmentLeave.Value
		};
		DialogResult = DialogResult.OK;
		Close();
	}
}
