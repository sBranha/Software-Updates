using System;

namespace DavidCompanion;

[Serializable]
public class DavidLicenseInfo
{
	public string id { get; set; }

	public string publicId { get; set; }

	public string customerName { get; set; }

	public string customerEmail { get; set; }

	public string type { get; set; }

	public string typeLabel { get; set; }

	public string status { get; set; }

	public int maxDevices { get; set; }

	public int? purchaseAmountCents { get; set; }

	public string cloudPlan { get; set; }

	public string expiresAt { get; set; }

	public bool neverExpires { get; set; }
}
