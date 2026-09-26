using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Windows.Forms;

namespace DavidCompanion;

public class RoundButton : Button
{
	public int Radius = 16;

	public RoundButton()
	{
		FlatStyle = FlatStyle.Flat;
		FlatAppearance.BorderSize = 0;
		Cursor = Cursors.Hand;
		EventHandler value = (object param0, EventArgs param1) =>
		{
			UpdateRegion();
		};
		Resize += value;
	}

	private void UpdateRegion()
	{
		if (Width < 2 || Height < 2)
		{
			return;
		}
		using GraphicsPath path = Ui.RoundPath(new Rectangle(0, 0, Width, Height), Radius);
		Region = new Region(path);
	}
}
