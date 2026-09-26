namespace DavidCompanion;

public class CloudChatMessage
{
	public string id { get; set; }

	public string deviceId { get; set; }

	public string senderKind { get; set; }

	public string senderUserId { get; set; }

	public string senderName { get; set; }

	public string body { get; set; }

	public string inReplyTo { get; set; }

	public string quickReply { get; set; }

	public string createdAt { get; set; }

	public string seenAt { get; set; }
}
