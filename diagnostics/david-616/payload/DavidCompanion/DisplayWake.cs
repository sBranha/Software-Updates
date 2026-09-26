using System.Runtime.InteropServices;

namespace DavidCompanion;

public static class DisplayWake
{
	private const uint ES_CONTINUOUS = 2147483648u;

	private const uint ES_SYSTEM_REQUIRED = 1u;

	private const uint ES_DISPLAY_REQUIRED = 2u;

	[DllImport("kernel32.dll")]
	private static extern uint SetThreadExecutionState(uint esFlags);

	public static void Wake()
	{
		try
		{
			SetThreadExecutionState(2147483651u);
		}
		catch
		{
		}
	}
}
