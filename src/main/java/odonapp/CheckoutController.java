package odonapp;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Random;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.Result;
import com.braintreegateway.Transaction;
import com.braintreegateway.Transaction.Status;
import com.braintreegateway.TransactionRequest;
import com.braintreegateway.CreditCard;
import com.braintreegateway.Customer;
import com.braintreegateway.ValidationError;
import com.braintreegateway.PaymentMethodRequest;
import com.braintreegateway.PaymentMethod;
import com.braintreegateway.PaymentMethodOptionsRequest;
import com.braintreegateway.Customer;
import com.braintreegateway.CustomerRequest;


import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CheckoutController {

    private BraintreeGateway gateway = Application.gateway;

    private Random random = new Random();
    private String paymentToken;

     private Status[] TRANSACTION_SUCCESS_STATUSES = new Status[] {
        Transaction.Status.AUTHORIZED,
        Transaction.Status.AUTHORIZING,
        Transaction.Status.SETTLED,
        Transaction.Status.SETTLEMENT_CONFIRMED,
        Transaction.Status.SETTLEMENT_PENDING,
        Transaction.Status.SETTLING,
        Transaction.Status.SUBMITTED_FOR_SETTLEMENT
     };

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String root(Model model) {
        return "redirect:checkouts";
    }

    @RequestMapping(value = "/checkouts", method = RequestMethod.GET)
    public String checkout(Model model) {
        String clientToken = gateway.clientToken().generate();
        model.addAttribute("clientToken", clientToken);
        System.out.println("checkout called, clientToken generated\n");
        
        return "checkouts/new";
    }

    /*
    *  This is an API call to receive the nonce, verify the card, store it in the vault, and should create the transacton.
    */
    @RequestMapping(value = "/checkouts", method = RequestMethod.POST)
    public String postForm(@RequestParam("amount") String amount, @RequestParam("payment_method_nonce") String nonce, Model model, final RedirectAttributes redirectAttributes) {
        System.out.println("postForm called, nonce received\n");
        
        BigDecimal decimalAmount;
        try {
            decimalAmount = new BigDecimal(amount);
        } catch (NumberFormatException e) {
            redirectAttributes.addFlashAttribute("errorDetails", "Error: 81503: Amount is an invalid format.");
            return "redirect:checkouts";
        }

        //Create customer, verify card, store in vault via new customer creation.
        CustomerRequest custRequest = new CustomerRequest()
            .creditCard()
                .paymentMethodNonce(nonce)
                .options()
                    .verifyCard(true)
                    .done()
                .done();    
        Result<Customer> custResult = gateway.customer().create(custRequest);
        System.out.println("Customer created\n");

        if(custResult.isSuccess()) {
            Customer customer = custResult.getTarget();
            paymentToken = customer.getPaymentMethods().get(0).getToken();
        }

        //Create a transaction based off of stored method
        TransactionRequest request = new TransactionRequest()
            .amount(decimalAmount)
            .paymentMethodToken(paymentToken)
            .options()
                .submitForSettlement(true) 
                .done();
        System.out.println("TransactionRequest created\n");                

        Result<Transaction> result = gateway.transaction().sale(request);

        if (result.isSuccess()) {
            Transaction transaction = result.getTarget();
            System.out.println("Transaction success\n");
            return "redirect:checkouts/" + transaction.getId();
        } else if (result.getTransaction() != null) {
            Transaction transaction = result.getTransaction();
            return "redirect:checkouts/" + transaction.getId();
        } else {
            String errorString = "";
            for (ValidationError error : result.getErrors().getAllDeepValidationErrors()) {
               errorString += "Error: " + error.getCode() + ": " + error.getMessage() + "\n";
            }
            redirectAttributes.addFlashAttribute("errorDetails", errorString);
            return "redirect:checkouts";
        }
    }

    @RequestMapping(value = "/checkouts/{transactionId}")
    public String getTransaction(@PathVariable String transactionId, Model model) {
        Transaction transaction;
        CreditCard creditCard;
        Customer customer;

        try {
            transaction = gateway.transaction().find(transactionId);
            creditCard = transaction.getCreditCard();
            customer = transaction.getCustomer();
        } catch (Exception e) {
            System.out.println("Exception: " + e);
            return "redirect:/checkouts";
        }

        model.addAttribute("isSuccess", Arrays.asList(TRANSACTION_SUCCESS_STATUSES).contains(transaction.getStatus()));
        model.addAttribute("transaction", transaction);
        model.addAttribute("creditCard", creditCard);
        model.addAttribute("customer", customer);

        return "checkouts/show";
    }
}