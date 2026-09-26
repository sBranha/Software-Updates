using System;

namespace DavidCompanion;

[Serializable]
public class DavidServiceStatusResponse
{
	public bool ok { get; set; }

	public string mode { get; set; }

	public bool serviceAvailable { get; set; }

	public bool supportNeeded { get; set; }

	public string supportMessage { get; set; }

	public string supportUrl { get; set; }

	public bool showSupportAlways { get; set; }

	public string suspendedMessage { get; set; }

	public string serverTime { get; set; }

	public string error { get; set; }
}
