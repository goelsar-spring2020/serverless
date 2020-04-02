import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;

import java.time.Instant;
import java.util.UUID;

public class SendEmail implements RequestHandler<SNSEvent, Object> {

    private DynamoDB dynamo;
    private final String TABLE_NAME = "csye6225";
    private Regions REGION = Regions.US_EAST_1;
    static final String from = System.getenv("emailAddress");
    static final String emailSubject = "Upcoming Due Bills";
    private String body;
    static String username;

    //Calculation of Time To Live(TTL)
    int SECONDS_IN_60_MINUTES = 60 * 60;
    long secondsSinceEpoch = Instant.now().getEpochSecond();
    long expirationTime = secondsSinceEpoch + SECONDS_IN_60_MINUTES;

    @Override
    public Object handleRequest(SNSEvent request, Context context) {

        LambdaLogger logger = context.getLogger();

        if (request.getRecords() == null) {
            logger.log("No records found!");
            return null;
        }

        //logging the Events
        logger.log("domain" + from);
        logger.log("SNS event=" + request);
        logger.log("Context=" + context);
        logger.log("TTL expirationTime=" + expirationTime);

        //Function Execution for extracting the message & user email
        String billMessage = "";
        billMessage = request.getRecords().get(0).getSNS().getMessage();
        logger.log("Subscribed Message from SNS" + billMessage);
        String token = UUID.randomUUID().toString();
        String sample = billMessage;
        username = sample.split(",")[0];


        //logging the User Email Address
        logger.log("Email Address=" + username);
        this.initDynamoDbClient();

        Item item = this.dynamo.getTable(TABLE_NAME).getItem("id", username);

        if (item == null || (item != null && Long.parseLong(item.get("TTL").toString()) < Instant.now().getEpochSecond())) {
            this.dynamo.getTable(TABLE_NAME).putItem(new PutItemSpec()
                    .withItem(new Item().withString("id", username)
                            .withString("Token", token).withLong("TTL", expirationTime)));

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Hi, \n" + "\nPlease find below the due bill links:");

            logger.log("Before" + billMessage);
            String[] bills = billMessage.split(",");

            for (int i = 1; i < bills.length; i++) {
                logger.log("Appending in loop" + bills[i]);
                stringBuilder.append("\n");
                stringBuilder.append(bills[i]);
            }

            stringBuilder.append("\nRegards,\nSarthak");
            logger.log("Assigning Body");
            this.body = stringBuilder.toString();
            logger.log("Assigning Body"+this.body);

            try {
                Content subject = new Content().withData(emailSubject);
                Content textbody = new Content().withData(body);
                Body body = new Body().withText(textbody);
                Message message = new Message().withSubject(subject).withBody(body);

                SendEmailRequest emailRequest = new SendEmailRequest()
                        .withDestination(new Destination().withToAddresses(username)).withMessage(message).withSource(from);

                AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.standard()
                        .withRegion(Regions.US_EAST_1).build();

                client.sendEmail(emailRequest);
                logger.log("Email sent successfully!");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }

    //creating a DynamoDB Client
    private void initDynamoDbClient() {
        AmazonDynamoDBClient client = new AmazonDynamoDBClient();
        client.setRegion(Region.getRegion(REGION));
        this.dynamo = new DynamoDB(client);
    }
}

