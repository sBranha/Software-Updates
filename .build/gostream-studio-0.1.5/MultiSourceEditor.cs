using System.Text.Json;
using GoStreamStudio.Core;

namespace GoStreamStudio.Controls;

public sealed class MultiSourceEditor : UserControl
{
    private readonly GoStreamController _controller;
    private readonly Action<string> _log;
    private readonly CheckBox _master = new();
    private readonly ComboBox _placeIn = new();
    private readonly ComboBox _background = new();
    private readonly Button[] _windowButtons = new Button[4];
    private readonly CheckBox _enabled = new();
    private readonly ComboBox _source = new();
    private readonly Dictionary<string, NumericUpDown> _values = new();
    private readonly MultiSourceCanvas _canvas = new();
    private readonly Label _capability = new();
    private readonly Label _presetName = new();
    private readonly Panel _colorPreview = new();
    private readonly WindowState[] _states = Enumerable.Range(0, 4).Select(_ => new WindowState()).ToArray();

    private bool _loading;
    private int _selectedWindow;
    private int _supportedWindows = 4;
    private WindowState? _clipboard;

    private static readonly Color[] WindowColors =
    {
        Color.FromArgb(139, 45, 55),
        Color.FromArgb(105, 105, 56),
        Color.FromArgb(51, 84, 69),
        Color.FromArgb(49, 55, 91)
    };

    public MultiSourceEditor(GoStreamController controller, Action<string> log)
    {
        _controller = controller;
        _log = log;
        Dock = DockStyle.Fill;
        BackColor = Theme.Background;
        BuildUi();
        SelectWindow(0);
    }

    private void BuildUi()
    {
        var root = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 2,
            RowCount = 1,
            BackColor = Theme.Background,
            Padding = new Padding(8)
        };
        root.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 270));
        root.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        Controls.Add(root);

        var sidebar = BuildSidebar();
        root.Controls.Add(sidebar, 0, 0);

        var right = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 2,
            BackColor = Theme.Background,
            Margin = new Padding(8, 0, 0, 0)
        };
        right.RowStyles.Add(new RowStyle(SizeType.Absolute, 54));
        right.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        root.Controls.Add(right, 1, 0);

        right.Controls.Add(BuildToolbar(), 0, 0);
        _canvas.Dock = DockStyle.Fill;
        _canvas.Margin = new Padding(0, 8, 0, 0);
        _canvas.SetStates(_states, _selectedWindow);
        right.Controls.Add(_canvas, 0, 1);
    }

    private Control BuildSidebar()
    {
        var side = new Panel
        {
            Dock = DockStyle.Fill,
            BackColor = Theme.Surface,
            Padding = new Padding(10)
        };

        var y = 10;
        AddFieldLabel(side, "Place In", y);
        _placeIn.DropDownStyle = ComboBoxStyle.DropDownList;
        _placeIn.Items.AddRange(new object[] { "Background", "Foreground" });
        _placeIn.SelectedIndex = 0;
        _placeIn.SetBounds(92, y - 4, 158, 30);
        _placeIn.SelectedIndexChanged += async (_, _) =>
        {
            if (!_loading && _placeIn.SelectedIndex >= 0)
                await _controller.SetMultiSourcePlaceInAsync(_placeIn.SelectedIndex);
        };
        side.Controls.Add(_placeIn);

        y += 36;
        AddFieldLabel(side, "Background", y);
        _background.DropDownStyle = ComboBoxStyle.DropDownList;
        _background.SetBounds(92, y - 4, 158, 30);
        _background.SelectedIndexChanged += async (_, _) =>
        {
            if (!_loading && _background.SelectedItem is SourceItem s)
                await _controller.SetMultiSourceFillSourceAsync(s.Id);
        };
        side.Controls.Add(_background);

        y += 40;
        for (var i = 0; i < 4; i++)
        {
            var index = i;
            var b = new Button
            {
                Text = (i + 1).ToString(),
                Width = 48,
                Height = 30,
                Location = new Point(10 + i * 56, y),
                FlatStyle = FlatStyle.Flat,
                BackColor = WindowColors[i],
                ForeColor = Color.White,
                Font = new Font("Segoe UI Semibold", 9f)
            };
            b.FlatAppearance.BorderSize = 1;
            b.FlatAppearance.BorderColor = Theme.Border;
            b.Click += (_, _) => SelectWindow(index);
            _windowButtons[i] = b;
            side.Controls.Add(b);
        }

        y += 38;
        var copy = SmallButton("Copy", 10, y, 72);
        copy.Click += (_, _) =>
        {
            PullState();
            _clipboard = _states[_selectedWindow].Clone();
            _log($"MultiSource Window {_selectedWindow + 1} copied.");
        };
        side.Controls.Add(copy);

        var paste = SmallButton("Paste", 91, y, 72);
        paste.Click += async (_, _) =>
        {
            if (_clipboard is null) return;
            var keepSource = _clipboard.Source;
            LoadSelectedState(_clipboard.Clone());
            _states[_selectedWindow].Source = keepSource;
            await ApplySelectedWindowAsync();
            _log($"MultiSource settings pasted to Window {_selectedWindow + 1}.");
        };
        side.Controls.Add(paste);

        var reset = SmallButton("Reset", 172, y, 78);
        reset.Click += async (_, _) =>
        {
            var source = _states[_selectedWindow].Source;
            LoadSelectedState(new WindowState { Source = source, Enabled = true, Scale = 45 });
            await ApplySelectedWindowAsync();
            _log($"MultiSource Window {_selectedWindow + 1} reset.");
        };
        side.Controls.Add(reset);

        y += 42;
        _master.Text = "MultiSource Enabled";
        _master.AutoSize = true;
        _master.ForeColor = Theme.Text;
        _master.BackColor = Theme.Surface;
        _master.Location = new Point(10, y);
        _master.CheckedChanged += async (_, _) =>
        {
            if (_loading) return;
            await _controller.SetMultiSourceEnabledAsync(_master.Checked);
        };
        side.Controls.Add(_master);

        y += 31;
        _enabled.Text = "Window Enabled";
        _enabled.AutoSize = true;
        _enabled.ForeColor = Theme.Text;
        _enabled.BackColor = Theme.Surface;
        _enabled.Location = new Point(10, y);
        _enabled.CheckedChanged += async (_, _) =>
        {
            _states[_selectedWindow].Enabled = _enabled.Checked;
            RefreshCanvas();
            if (!_loading && IsSelectedSupported())
                await _controller.SetMultiSourceWindowEnabledAsync(_selectedWindow, _enabled.Checked);
        };
        side.Controls.Add(_enabled);

        y += 34;
        AddFieldLabel(side, "Source", y);
        _source.DropDownStyle = ComboBoxStyle.DropDownList;
        _source.SetBounds(92, y - 4, 158, 30);
        _source.SelectedIndexChanged += async (_, _) =>
        {
            if (_source.SelectedItem is not SourceItem s) return;
            _states[_selectedWindow].Source = s.Id;
            RefreshCanvas();
            if (!_loading && IsSelectedSupported())
                await _controller.SetMultiSourceWindowSourceAsync(_selectedWindow, s.Id);
        };
        side.Controls.Add(_source);

        y += 39;
        AddNumeric(side, "Position X", "X", ref y, -100, 100, 0.1m, 1);
        AddNumeric(side, "Position Y", "Y", ref y, -100, 100, 0.1m, 1);
        AddNumeric(side, "Scale", "Scale", ref y, 1, 200, 1, 0);

        y += 2;
        AddSectionLabel(side, "Crop (%)", ref y);
        AddNumeric(side, "Left", "CropL", ref y, 0, 100, 1, 0);
        AddNumeric(side, "Right", "CropR", ref y, 0, 100, 1, 0);
        AddNumeric(side, "Top", "CropT", ref y, 0, 100, 1, 0);
        AddNumeric(side, "Bottom", "CropB", ref y, 0, 100, 1, 0);
        AddNumeric(side, "Corner", "Corner", ref y, 0, 100, 1, 0);

        y += 2;
        AddSectionLabel(side, "Border", ref y);
        AddNumeric(side, "Width", "Border", ref y, 0, 100, 1, 0);
        AddNumeric(side, "Hue", "Hue", ref y, 0, 359, 1, 0);
        AddNumeric(side, "Saturation", "Saturation", ref y, 0, 100, 1, 0);
        AddNumeric(side, "Brightness", "Brightness", ref y, 0, 100, 1, 0);

        AddFieldLabel(side, "Color", y);
        _colorPreview.SetBounds(92, y - 2, 158, 24);
        _colorPreview.BackColor = HsvToColor(45, 100, 100);
        _colorPreview.BorderStyle = BorderStyle.FixedSingle;
        side.Controls.Add(_colorPreview);

        foreach (var pair in _values)
        {
            var key = pair.Key;
            pair.Value.ValueChanged += async (_, _) =>
            {
                PullState();
                RefreshCanvas();
                if (!_loading && IsSelectedSupported())
                    await SendSelectedValueAsync(key, pair.Value.Value);
            };
        }

        return side;
    }

    private Control BuildToolbar()
    {
        var bar = new Panel { Dock = DockStyle.Fill, BackColor = Theme.Surface };

        var undo = SmallButton("↶", 8, 10, 42);
        undo.Enabled = false;
        bar.Controls.Add(undo);

        var redo = SmallButton("↷", 57, 10, 42);
        redo.Enabled = false;
        bar.Controls.Add(redo);

        var load = SmallButton("Load preset", 112, 10, 108);
        load.Click += (_, _) => LoadPreset();
        bar.Controls.Add(load);

        var save = SmallButton("Save preset", 228, 10, 108);
        save.Click += (_, _) => SavePreset();
        bar.Controls.Add(save);

        var browse = SmallButton("Browse Presets", 344, 10, 120);
        browse.Click += (_, _) => LoadPreset();
        bar.Controls.Add(browse);

        _presetName.Text = "multisource";
        _presetName.ForeColor = Theme.Muted;
        _presetName.AutoSize = true;
        _presetName.Location = new Point(480, 19);
        bar.Controls.Add(_presetName);

        _capability.Text = "4 MultiSource windows";
        _capability.ForeColor = Theme.Muted;
        _capability.AutoSize = true;
        _capability.Anchor = AnchorStyles.Top | AnchorStyles.Right;
        _capability.Location = new Point(Math.Max(0, bar.Width - 220), 19);
        bar.Controls.Add(_capability);
        bar.Resize += (_, _) => _capability.Location = new Point(Math.Max(0, bar.ClientSize.Width - _capability.Width - 14), 19);

        return bar;
    }

    public void SetSources(IReadOnlyList<int> ids, int supportedWindows)
    {
        var sources = ids.Count > 0 ? ids.Distinct().ToArray() : Enumerable.Range(1, 12).ToArray();
        _supportedWindows = Math.Clamp(supportedWindows, 1, 4);

        _loading = true;
        try
        {
            FillSourceCombo(_background, sources, (_background.SelectedItem as SourceItem)?.Id ?? sources[0]);
            FillSourceCombo(_source, sources, _states[_selectedWindow].Source);

            for (var i = 0; i < 4; i++)
            {
                _windowButtons[i].Enabled = i < _supportedWindows;
                _windowButtons[i].Text = i < _supportedWindows ? (i + 1).ToString() : $"{i + 1} ×";
            }

            if (_selectedWindow >= _supportedWindows)
                SelectWindow(0);

            _capability.Text = _supportedWindows == 4
                ? "4 MultiSource windows available"
                : "2 MultiSource windows available on this model";
        }
        finally
        {
            _loading = false;
        }
    }

    private void SelectWindow(int index)
    {
        if (index < 0 || index >= _supportedWindows) return;

        PullState();
        _selectedWindow = index;

        _loading = true;
        try
        {
            for (var i = 0; i < 4; i++)
            {
                _windowButtons[i].FlatAppearance.BorderColor = i == index ? Color.White : Theme.Border;
                _windowButtons[i].FlatAppearance.BorderSize = i == index ? 2 : 1;
            }

            var s = _states[index];
            _enabled.Checked = s.Enabled;
            SelectSource(_source, s.Source);
            SetValue("X", s.X);
            SetValue("Y", s.Y);
            SetValue("Scale", s.Scale);
            SetValue("CropL", s.CropL);
            SetValue("CropR", s.CropR);
            SetValue("CropT", s.CropT);
            SetValue("CropB", s.CropB);
            SetValue("Corner", s.Corner);
            SetValue("Border", s.Border);
            SetValue("Hue", s.Hue);
            SetValue("Saturation", s.Saturation);
            SetValue("Brightness", s.Brightness);
            UpdateColorPreview();
        }
        finally
        {
            _loading = false;
        }

        RefreshCanvas();
    }

    private void PullState()
    {
        if (_values.Count == 0) return;
        var s = _states[_selectedWindow];
        s.Enabled = _enabled.Checked;
        if (_source.SelectedItem is SourceItem source) s.Source = source.Id;
        s.X = GetValue("X");
        s.Y = GetValue("Y");
        s.Scale = GetValue("Scale");
        s.CropL = GetValue("CropL");
        s.CropR = GetValue("CropR");
        s.CropT = GetValue("CropT");
        s.CropB = GetValue("CropB");
        s.Corner = GetValue("Corner");
        s.Border = GetValue("Border");
        s.Hue = GetValue("Hue");
        s.Saturation = GetValue("Saturation");
        s.Brightness = GetValue("Brightness");
        UpdateColorPreview();
    }

    private void LoadSelectedState(WindowState state)
    {
        _states[_selectedWindow] = state.Clone();
        SelectWindow(_selectedWindow);
    }

    private bool IsSelectedSupported() => _selectedWindow < _supportedWindows;

    private async Task SendSelectedValueAsync(string key, decimal value)
    {
        switch (key)
        {
            case "X": await _controller.SetMultiSourceWindowXAsync(_selectedWindow, value); break;
            case "Y": await _controller.SetMultiSourceWindowYAsync(_selectedWindow, value); break;
            case "Scale": await _controller.SetMultiSourceWindowSizeAsync(_selectedWindow, value); break;
            case "CropL": await _controller.SetMultiSourceWindowCropLeftAsync(_selectedWindow, value); break;
            case "CropR": await _controller.SetMultiSourceWindowCropRightAsync(_selectedWindow, value); break;
            case "CropT": await _controller.SetMultiSourceWindowCropTopAsync(_selectedWindow, value); break;
            case "CropB": await _controller.SetMultiSourceWindowCropBottomAsync(_selectedWindow, value); break;
            case "Corner": await _controller.SetMultiSourceWindowCornerRadiusAsync(_selectedWindow, value); break;
            case "Border": await _controller.SetMultiSourceWindowBorderWidthAsync(_selectedWindow, value); break;
            case "Hue": await _controller.SetMultiSourceWindowBorderHueAsync(_selectedWindow, value); break;
            case "Saturation": await _controller.SetMultiSourceWindowBorderSaturationAsync(_selectedWindow, value); break;
            case "Brightness": await _controller.SetMultiSourceWindowBorderBrightnessAsync(_selectedWindow, value); break;
        }
    }

    private async Task ApplySelectedWindowAsync()
    {
        if (!IsSelectedSupported()) return;
        PullState();
        var s = _states[_selectedWindow];

        await _controller.SetMultiSourceWindowEnabledAsync(_selectedWindow, s.Enabled);
        await _controller.SetMultiSourceWindowSourceAsync(_selectedWindow, s.Source);
        await _controller.SetMultiSourceWindowXAsync(_selectedWindow, s.X);
        await _controller.SetMultiSourceWindowYAsync(_selectedWindow, s.Y);
        await _controller.SetMultiSourceWindowSizeAsync(_selectedWindow, s.Scale);
        await _controller.SetMultiSourceWindowCropLeftAsync(_selectedWindow, s.CropL);
        await _controller.SetMultiSourceWindowCropRightAsync(_selectedWindow, s.CropR);
        await _controller.SetMultiSourceWindowCropTopAsync(_selectedWindow, s.CropT);
        await _controller.SetMultiSourceWindowCropBottomAsync(_selectedWindow, s.CropB);
        await _controller.SetMultiSourceWindowCornerRadiusAsync(_selectedWindow, s.Corner);
        await _controller.SetMultiSourceWindowBorderWidthAsync(_selectedWindow, s.Border);
        await _controller.SetMultiSourceWindowBorderHueAsync(_selectedWindow, s.Hue);
        await _controller.SetMultiSourceWindowBorderSaturationAsync(_selectedWindow, s.Saturation);
        await _controller.SetMultiSourceWindowBorderBrightnessAsync(_selectedWindow, s.Brightness);
    }

    private void SavePreset()
    {
        PullState();

        using var dialog = new SaveFileDialog
        {
            Title = "Save MultiSource Preset",
            Filter = "VU-style MultiSource Preset (*.vumspreset)|*.vumspreset|GoStream MultiSource Preset (*.gms)|*.gms",
            DefaultExt = "vumspreset",
            AddExtension = true
        };
        if (dialog.ShowDialog(this) != DialogResult.OK) return;

        var preset = new MultiSourcePreset
        {
            Enabled = _master.Checked,
            PlaceIn = Math.Max(0, _placeIn.SelectedIndex),
            Background = (_background.SelectedItem as SourceItem)?.Id ?? 1,
            Windows = _states.Select(s => s.Clone()).ToArray()
        };

        File.WriteAllText(dialog.FileName, JsonSerializer.Serialize(preset, new JsonSerializerOptions { WriteIndented = true }));
        _presetName.Text = Path.GetFileNameWithoutExtension(dialog.FileName);
        _log("MultiSource preset saved.");
    }

    private async void LoadPreset()
    {
        using var dialog = new OpenFileDialog
        {
            Title = "Load MultiSource Preset",
            Filter = "MultiSource Presets (*.vumspreset;*.gms)|*.vumspreset;*.gms|All files (*.*)|*.*"
        };
        if (dialog.ShowDialog(this) != DialogResult.OK) return;

        try
        {
            var preset = JsonSerializer.Deserialize<MultiSourcePreset>(File.ReadAllText(dialog.FileName));
            if (preset is null) return;

            _loading = true;
            try
            {
                _master.Checked = preset.Enabled;
                _placeIn.SelectedIndex = Math.Clamp(preset.PlaceIn, 0, 1);
                SelectSource(_background, preset.Background);
                for (var i = 0; i < Math.Min(4, preset.Windows.Length); i++)
                    _states[i] = preset.Windows[i].Clone();
            }
            finally
            {
                _loading = false;
            }

            SelectWindow(Math.Min(_selectedWindow, _supportedWindows - 1));

            await _controller.SetMultiSourceEnabledAsync(_master.Checked);
            await _controller.SetMultiSourcePlaceInAsync(Math.Max(0, _placeIn.SelectedIndex));
            if (_background.SelectedItem is SourceItem bg)
                await _controller.SetMultiSourceFillSourceAsync(bg.Id);

            for (var i = 0; i < _supportedWindows; i++)
            {
                _selectedWindow = i;
                SelectWindow(i);
                await ApplySelectedWindowAsync();
            }

            SelectWindow(0);
            _presetName.Text = Path.GetFileNameWithoutExtension(dialog.FileName);
            _log("MultiSource preset loaded and applied.");
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, "Could not load preset: " + ex.Message,
                "GoStream Studio", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
    }

    private void AddNumeric(Control parent, string label, string key, ref int y,
        decimal min, decimal max, decimal increment, int decimals)
    {
        AddFieldLabel(parent, label, y);
        var n = new NumericUpDown
        {
            Location = new Point(92, y - 4),
            Width = 158,
            Height = 27,
            Minimum = min,
            Maximum = max,
            Increment = increment,
            DecimalPlaces = decimals,
            BackColor = Color.FromArgb(13, 28, 39),
            ForeColor = Theme.Text,
            BorderStyle = BorderStyle.FixedSingle
        };
        _values[key] = n;
        parent.Controls.Add(n);
        y += 31;
    }

    private static void AddFieldLabel(Control parent, string text, int y)
    {
        parent.Controls.Add(new Label
        {
            Text = text,
            ForeColor = Theme.Muted,
            Font = new Font("Segoe UI", 8.5f),
            AutoSize = true,
            Location = new Point(10, y + 2)
        });
    }

    private static void AddSectionLabel(Control parent, string text, ref int y)
    {
        parent.Controls.Add(new Label
        {
            Text = text,
            ForeColor = Color.White,
            Font = new Font("Segoe UI Semibold", 8.5f),
            AutoSize = true,
            Location = new Point(10, y + 3)
        });
        y += 27;
    }

    private static Button SmallButton(string text, int x, int y, int width)
    {
        var b = new Button
        {
            Text = text,
            Location = new Point(x, y),
            Width = width,
            Height = 30,
            FlatStyle = FlatStyle.Flat,
            BackColor = Theme.Surface2,
            ForeColor = Theme.Text,
            Font = new Font("Segoe UI", 8.5f)
        };
        b.FlatAppearance.BorderColor = Theme.Border;
        b.FlatAppearance.BorderSize = 1;
        return b;
    }

    private static void FillSourceCombo(ComboBox combo, IReadOnlyList<int> ids, int preferred)
    {
        combo.Items.Clear();
        foreach (var id in ids) combo.Items.Add(new SourceItem(id));
        if (combo.Items.Count == 0) return;
        SelectSource(combo, preferred);
    }

    private static void SelectSource(ComboBox combo, int id)
    {
        if (combo.Items.Count == 0) return;
        for (var i = 0; i < combo.Items.Count; i++)
        {
            if (combo.Items[i] is SourceItem s && s.Id == id)
            {
                combo.SelectedIndex = i;
                return;
            }
        }
        combo.SelectedIndex = 0;
    }

    private decimal GetValue(string key) => _values.TryGetValue(key, out var n) ? n.Value : 0;

    private void SetValue(string key, decimal value)
    {
        if (!_values.TryGetValue(key, out var n)) return;
        n.Value = Math.Clamp(value, n.Minimum, n.Maximum);
    }

    private void RefreshCanvas()
    {
        _canvas.SetStates(_states, _selectedWindow);
        _canvas.Invalidate();
        UpdateColorPreview();
    }

    private void UpdateColorPreview()
    {
        if (_values.Count == 0) return;
        _colorPreview.BackColor = HsvToColor(
            (double)GetValue("Hue"),
            (double)GetValue("Saturation"),
            (double)GetValue("Brightness"));
    }

    private static Color HsvToColor(double hue, double saturation, double brightness)
    {
        saturation = Math.Clamp(saturation / 100.0, 0, 1);
        brightness = Math.Clamp(brightness / 100.0, 0, 1);
        hue = ((hue % 360) + 360) % 360;

        var c = brightness * saturation;
        var x = c * (1 - Math.Abs((hue / 60.0) % 2 - 1));
        var m = brightness - c;
        double r = 0, g = 0, b = 0;

        if (hue < 60) { r = c; g = x; }
        else if (hue < 120) { r = x; g = c; }
        else if (hue < 180) { g = c; b = x; }
        else if (hue < 240) { g = x; b = c; }
        else if (hue < 300) { r = x; b = c; }
        else { r = c; b = x; }

        return Color.FromArgb(
            (int)Math.Round((r + m) * 255),
            (int)Math.Round((g + m) * 255),
            (int)Math.Round((b + m) * 255));
    }

    private sealed record SourceItem(int Id)
    {
        public override string ToString() => $"CAM {Id}";
    }

    public sealed class MultiSourcePreset
    {
        public bool Enabled { get; set; }
        public int PlaceIn { get; set; }
        public int Background { get; set; } = 1;
        public WindowState[] Windows { get; set; } = Array.Empty<WindowState>();
    }

    public sealed class WindowState
    {
        public bool Enabled { get; set; } = true;
        public int Source { get; set; } = 1;
        public decimal X { get; set; }
        public decimal Y { get; set; }
        public decimal Scale { get; set; } = 45;
        public decimal CropL { get; set; }
        public decimal CropR { get; set; }
        public decimal CropT { get; set; }
        public decimal CropB { get; set; }
        public decimal Corner { get; set; }
        public decimal Border { get; set; }
        public decimal Hue { get; set; } = 45;
        public decimal Saturation { get; set; } = 100;
        public decimal Brightness { get; set; } = 100;

        public WindowState Clone() => (WindowState)MemberwiseClone();
    }

    private sealed class MultiSourceCanvas : Panel
    {
        private WindowState[] _states = Enumerable.Range(0, 4).Select(_ => new WindowState()).ToArray();
        private int _selected;

        public MultiSourceCanvas()
        {
            DoubleBuffered = true;
            BackColor = Color.FromArgb(13, 18, 28);
        }

        public void SetStates(WindowState[] states, int selected)
        {
            _states = states.Select(s => s.Clone()).ToArray();
            _selected = selected;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);

            var pad = 22;
            var area = new Rectangle(pad, pad, Math.Max(50, Width - pad * 2), Math.Max(50, Height - pad * 2));
            var w = area.Width;
            var h = (int)(w * 9.0 / 16.0);
            if (h > area.Height)
            {
                h = area.Height;
                w = (int)(h * 16.0 / 9.0);
            }

            var frame = new Rectangle(
                area.X + (area.Width - w) / 2,
                area.Y + (area.Height - h) / 2,
                w,
                h);

            using var background = new SolidBrush(Color.FromArgb(10, 16, 24));
            e.Graphics.FillRectangle(background, frame);

            for (var i = 0; i < 4; i++)
            {
                var s = _states[i];
                if (!s.Enabled) continue;

                var scale = Math.Clamp((double)s.Scale / 100.0, .05, 2.0);
                var rw = (int)(frame.Width * scale);
                var rh = (int)(rw * 9.0 / 16.0);

                var cx = frame.Left + frame.Width / 2.0 + (double)s.X / 100.0 * frame.Width / 2.0;
                var cy = frame.Top + frame.Height / 2.0 - (double)s.Y / 100.0 * frame.Height / 2.0;
                var rect = new Rectangle((int)(cx - rw / 2.0), (int)(cy - rh / 2.0), rw, rh);

                var fillColor = WindowColors[i];
                using var fill = new SolidBrush(Color.FromArgb(160, fillColor));
                using var pen = new Pen(i == _selected ? Color.White : Color.FromArgb(180, 190, 200), i == _selected ? 3f : 1.5f);

                e.Graphics.FillRectangle(fill, rect);
                e.Graphics.DrawRectangle(pen, rect);

                var handle = 7;
                using var handleBrush = new SolidBrush(Color.White);
                foreach (var p in new[]
                {
                    new Point(rect.Left, rect.Top),
                    new Point(rect.Right, rect.Top),
                    new Point(rect.Left, rect.Bottom),
                    new Point(rect.Right, rect.Bottom),
                    new Point(rect.Left + rect.Width / 2, rect.Top),
                    new Point(rect.Left + rect.Width / 2, rect.Bottom),
                    new Point(rect.Left, rect.Top + rect.Height / 2),
                    new Point(rect.Right, rect.Top + rect.Height / 2)
                })
                    e.Graphics.FillRectangle(handleBrush, p.X - handle / 2, p.Y - handle / 2, handle, handle);

                using var font = new Font("Segoe UI Semibold", Math.Max(14f, Math.Min(44f, rect.Height / 4f)));
                using var brush = new SolidBrush(Color.White);
                var label = (i + 1).ToString();
                var size = e.Graphics.MeasureString(label, font);
                e.Graphics.DrawString(label, font, brush,
                    rect.Left + (rect.Width - size.Width) / 2,
                    rect.Top + (rect.Height - size.Height) / 2);
            }

            using var outline = new Pen(Theme.Border, 1);
            e.Graphics.DrawRectangle(outline, frame);
        }
    }
}
