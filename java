var timeout = null;


var dante = (function ($) {
    var CartToken;
    var AuthToken;
    var BaseURL;
    var CallBacks = [];

    return {
        setup: function () {//Always called
            CartToken = getCookie("dante-carttoken");
            AuthToken = getCookie("dante-authtoken");
            BaseURL = document.getElementById("dantebaseurl").value;
        },
        initialise: function (action = null) {//Called by user
            if (document.readyState !== 'loading')//DOM already loaded, so let's initialise, else wait
                Initialise(action);
            else {
                document.addEventListener("DOMContentLoaded", function (event) {
                    Initialise(action);
                });
            }

            window.addEventListener('popstate', function () {//Checks for user using the back button on browser
                loadPage();
            }, false);
        },

        /* \/ \/ CART \/ \/ */
        loadCart: function (reload) {
            load("/Checkout/Cart", { carttoken: CartToken }, "Cart", reload);
        },
        reloadCart: function () {//Only loads cart if it's already on page
            if (jQuery("#dante .dante-Cart").length > 0) {
                this.loadCart(true);
            }
        },
        addToCart: function (type, reference, quantity) {
            if (post("/Checkout/AddToCart", { CartToken: CartToken, type: type, reference: reference, quantity: quantity }).success) {
                this.reloadCart();
                return true;
            }
            else return false;
        },
        removeFromCart: function (type, reference, confirmation) {
            if (!confirmation) {
                AddElement("modalcontainer", "confirmmodal", ConfirmDialog("Remove Item", "Please confirm you would like to remove this item from the cart?", "<button class=\"button-command\" onclick=\"dante.removeFromCart(" + type + ", " + reference + ", true); return false;\" id=\"btnconfirm\">confirm</button>"));
                return;
            }
            else RemoveElement('confirmmodal');

            if (post("/Checkout/RemoveFromCart", { carttoken: CartToken, type: type, reference: reference }).success) {
                this.reloadCart();
                return true;
            }
            else return false;
        },
        clearCart: function (confirmation) {
            if (!confirmation) {
                AddElement("modalcontainer", "confirmmodal", ConfirmDialog("Clear Cart", "Please confirm you would like to clear all items your cart?", "<button class=\"button-command\" onclick=\"dante.clearCart(true); return false;\" id=\"btnconfirm\">confirm</button>"));
                return;
            }
            else RemoveElement('confirmmodal');
            setCookie("dante-carttoken", "", -1);//expire the cookie
            CartToken = null;
            this.reloadCart();
        },
        onApplyVoucherCode: function () {
            if (post("/Checkout/ApplyVoucherCode", jQuery("#dante #vouchercodeform").serialize()).success) {
                this.reloadCart();
            }
            return false;
        },
        updateScheduleQuantity: function (id, value) {
            var tableRow = jQuery("tr[data-scheduleid='" + id + "']");
            var input = tableRow.find("input")[0];
            if (input != null) {
                var quantity = parseInt(input.value) + value;
                if (quantity >= 1 && quantity <= parseInt(tableRow.data("placesleft"))) {
                    DeActive("btncheckout");
                    input.value = quantity;

                    clearTimeout(timeout);
                    timeout = setTimeout(function () {
                        var currentValue = parseInt(tableRow.find("input")[0].value);
                        if (quantity == currentValue) {
                            if (post("/Checkout/UpdateQuantity", { carttoken: CartToken, type: 1, reference: id, quantity: quantity }))
                                dante.reloadCart();
                        }
                        Active("btncheckout");
                    }, 500);
                }
            }
            return false;
        },
        updateProductQuantity: function (id, value) {
            var div = jQuery("div[data-productid='" + id + "']");
            var input = div.find("input")[0];
            if (input != null) {
                var quantity = parseInt(input.value) + value;
                if (quantity >= 0 && quantity <= 99) {
                    input.value = quantity;
                    DeActive("btncheckout");
                    clearTimeout(timeout);
                    timeout = setTimeout(function () {
                        var currentValue = parseInt(div.find("input")[0].value);
                        if (quantity == currentValue) {
                            if (quantity > 0)
                                dante.addToCart(8, id, quantity);
                            else if (div.data("productcartid") != null)
                                dante.removeFromCart(8, id, true);
                        }
                        Active("btncheckout");
                    }, 500);
                }
            }
            return false;
        },
        OnTermsAndConditions: function (element) {
            if (element != null && element.checked == false)
                return;
            if (element.checked == false)
                element.checked == true;//safety as on some systems the element wasn't being checked

            var modal = jQuery("#dante #termsandconditionsmodal");

            if (modal.length > 0) {
                modal.show();
                return;
            }

            jQuery.ajax(
                {
                    type: "GET",
                    url: BaseURL + "/Client/Checkout/TermsAndConditions",
                    dataType: "json",
                    success: function (data) {
                        if (data.TermsAndConditions != null)
                            AddElement("modalcontainer", "errormodal", TermsAndConditionsDialog("Terms And Conditions", data.TermsAndConditions));
                    },
                    error: function (xhr, error, status) {
                        AddElement("modalcontainer", "errormodal", ErrorDialog("Error", status));
                    }
                });
            return false;
        },
        /* /\ /\ CART /\ /\ */

        /* \/ \/ AUTHENTICATION \/ \/ */

        checkLogin: function (returntoaction) {
            if (returntoaction == null)
                returntoaction = getParameterValue("returntoaction");

            if (AuthToken == null || AuthToken == "") {
                load("/Authentication/Login", { carttoken: CartToken, returntoaction: returntoaction }, "Login", false, returntoaction != null ? [{ name: "returntoaction", value: returntoaction }] : null);
                loginSetup();
                return false;
            }
            else
                return true;
        },
        onLogin: function () {
            var response = post("/Authentication/Login", jQuery("#dante #loginform").serialize());
            if (response.success) {
                if (response.data.PrivateDelegateMessage) {
                    AddElement("modalcontainer", "errormodal", ErrorDialog("Private Delegate", response.data.PrivateDelegateMessage));
                    this.loadCart();
                }
                else {
                    var returnToAction = getParameterValue("returntoaction");
                    if (returnToAction != null)
                        loadPage(returnToAction);
                    else
                        this.loadCart();//No action passed, so let's reload the page instead
                }
            }
        },
        onLoginCheckEmailExists: function () {
            var response = post("/Authentication/LoginCheckEmailExists", { email: jQuery('#registeremailaddress').val(), sendaccesscode: true })

            if (response.data.ResetPasswordTokenID != null) {
                jQuery('#dante #temporaryaccessmodal').show();
                jQuery('#dante #resetpasswordtokenid').val(response.data.ResetPasswordTokenID);
            }
            else {
                if (!response.success && response.data.Message == null) {
                    //Email account does not exist and no error, continue to register
                    this.loadRegister(jQuery('#registeremailaddress').val());
                }
            }
        },
        onLoginTemporaryAccess: function () {
            var response = post("/Authentication/LoginTemporaryAccess", jQuery("#dante #temporaryaccessform").serialize());
            if (response.success) {
                if (response.data.PrivateDelegateMessage) {
                    AddElement("modalcontainer", "errormodal", ErrorDialog("Private Delegate", response.data.PrivateDelegateMessage));
                    this.loadCart();
                }
                else {
                    var returnToAction = getParameterValue("returntoaction");
                    if (returnToAction != null)
                        loadPage(returnToAction);
                    else
                        this.loadCart();//No action passed, so let's reload the page instead
                }
            }
        },
        logout: function () {
            var response = post("/Authentication/Logout", { carttoken: CartToken });
            if (response.success) {
                if (response.data.ClearCart) {
                    setCookie("dante-carttoken", "", -1);
                    CartToken = null;
                }
            }
            //Even if not success on the server, still log them out in the front end
            setCookie("dante-authtoken", "", -1);
            AuthToken = null;
        },
        loadRegister: function (email) {
            var returnToAction = getParameterValue("returntoaction");
            load("/Authentication/Register", { carttoken: CartToken }, "Register", false, returnToAction != null ? [{ name: "returntoaction", value: returnToAction }] : null);
            jQuery('#email').val(email);
            DatePickers("#registerform div[class*='datepicker'] > input");
            ComboBoxes("#registerform .combobox");
        },
        onRegister: function () {
            DeActive("btnregister");
            var response = post("/Authentication/Register", jQuery("#dante #registerform").serialize());
            if (response.success) {
                if (response.data.PrivateDelegateMessage) {
                    AddElement("modalcontainer", "errormodal", ErrorDialog("Private Delegate", response.data.PrivateDelegateMessage));
                    this.loadCart();
                }
                else {
                    var returnToAction = getParameterValue("returntoaction");
                    if (returnToAction != null)
                        loadPage(returnToAction);
                    else
                        this.loadCart();//No action passed, so let's reload the page instead
                }
            }
            Active("btnregister");
        },
        showPasswordReset: function () {
            jQuery('#rpemailaddress').val(jQuery('#emailaddress').val());
            jQuery('#resetpasswordmodal').show();
        },
        onResetPassword: function () {
            DeActive("btnResetPassword");
            var response = post("/Authentication/ResetPassword", jQuery("#dante #resetpasswordform").serialize());
            if (response.success) {
                jQuery('#resetpasswordmodal').hide();
                AddElement("modalcontainer", "informationmodal", InformationDialog("Password Reset", "An email with reset password instructions has been sent to you."), true);
            }
            Active("btnResetPassword");
        },
        loadResetPasswordReturn: function (s) {
            var returnToAction = getParameterValue("returntoaction");
            var token = getParameterValue('token');
            load("/Authentication/ResetPasswordReturn", { token: token, carttoken: CartToken, returnToAction: returnToAction }, "ResetPassword", reload = false, urlparameters = [{ name: "token", value: token }, { name: "returntoaction", value: returnToAction }]);
            jQuery('#dante #resetpasswordform #carttoken').val(CartToken);
        },
        onPasswordResetReturn: function () {
            var response = post("/Authentication/ResetPasswordReturn", jQuery("#dante #resetpasswordform").serialize());
            if (response.success) {
                if (response.data.ReturnToAction != null) {
                    loadPage(response.data.ReturnToAction);
                }
                else {
                    if (response.data.PrivateDelegateMessage) {
                        AddElement("modalcontainer", "errormodal", ErrorDialog("Private Delegate", response.data.PrivateDelegateMessage));
                        this.loadCart();
                    }
                    else {
                        this.loadCart();//redirect back to cart
                    }
                }
            }
        },
        formComboboxSearch: function (type, token, element) {
            var ulElement = jQuery(element).parent().children('ul');
            ulElement.empty();
            if (element.value.length < 3)
                return;
            clearTimeout(timeout);
            timeout = setTimeout(function () {
                jQuery.ajax(
                    {
                        type: "GET",
                        data: { type: type, token: token, input: element.value },
                        url: BaseURL + "/Client/Authentication/FormSearch",
                        dataType: "json",
                        success: function (data) {
                            if (data.Message != null && data.Message != "") {
                                if (document.getElementById("modalcontainer") != null)
                                    AddElement("modalcontainer", "errormodal", ErrorDialog(data.MessageHeader ? data.MessageHeader : "", data.Message.replace(/(?:\r\n|\r|\n)/g, '<br>')));
                            }
                            ulElement.empty();
                            for (var i = 0; i < data.Items.length; i++) {
                                ulElement.append('<li data-id="' + data.Items[i].Item1 + '">' + data.Items[i].Item2 + '</li>');
                            }
                        },
                        error: function (xhr, error, status) {
                            AddElement("modalcontainer", "errormodal", ErrorDialog("Error", status));
                        }
                    });
            }, 500);
        },
        /* /\ /\ AUTHENTICATION /\ /\ */

        /* \/ \/ SELECT DELEGATES \/ \/ */
        loadSelectDelegates: function (scheduleindex = null, reload = false) {
            if (scheduleindex == null)
                scheduleindex = getParameterValue('scheduleindex');

            if (scheduleindex == null)
                scheduleindex = 0;

            if (this.checkLogin("selectdelegates")) {
                load("/Checkout/SelectDelegates", { carttoken: CartToken, scheduleindex: scheduleindex }, "SelectDelegates", reload, urlparameters = [{ name: "scheduleindex", value: scheduleindex }]);
                DatePickers("#dante .delegate-form div[class*='datepicker'] > input");
                ComboBoxes("#dante .delegate-form .combobox");
                jQuery("#dante input[name$=FirstName], #dante input[name$=Surname]").keyup(function () {
                    SelectDelegateSearch(this);
                });

                jQuery("#dante input[name$=FirstName], #dante input[name$=Surname]").focusout(function () {
                    SelectDelegatesCheckExists(this);
                });
            }
        },
        OnClearDelegate: function (rowindex, confirmation) {
            var element = document.querySelector("div[data-rowindex='" + rowindex + "']");
            var delegateIDElement = element.querySelector("input[name$='ID']");

            if (!confirmation) {
                if (delegateIDElement.value != "")
                    AddElement("modalcontainer", "confirmmodal", ConfirmDialog("Clear Delegate", "Are you sure you want to clear the delegate?", '<button class="button-command" onclick="dante.OnClearDelegate(' + rowindex + ', true); return false;" id="btnconfirm">confirm</button>'));
                else {
                    if (jQuery('#quantityincart')[0].innerText == "1")
                        AddElement("modalcontainer", "errormodal", ErrorDialog("Unable to Remove", "You're unable to remove this delegate as you must have at least one delegate booked. "));
                    else
                        AddElement("modalcontainer", "confirmmodal",
                            ConfirmDialog("Remove Delegate from Cart", "Are you sure you want to remove this delegate from the cart? <br/><br/><b>This will reduce the total places booked.</b>",
                                '<button class="button-command" onclick="dante.OnClearDelegate(' + rowindex + ', true); return false;" id="btnconfirm">confirm</button>'));
                }
            }
            else {
                RemoveElement('confirmmodal');
                if (delegateIDElement.value != "") {//Clear delegate
                    this.OnSelectedDelegate(rowindex, null);
                }
                else {
                    var delegateRemovedElement = element.querySelector("input[name$='Removed']");
                    delegateRemovedElement.value = "true";
                    element.style.display = "none";
                    jQuery('#quantityincart')[0].innerText = jQuery('#quantityincart')[0].innerText - 1;
                }
            }
        },
        OnSelectedDelegate: function (rowindex, delegateid) {
            RemoveElement('confirmmodal');
            var delegateAdded = false;
            jQuery('.delegate-form [id$=id]').each(function () {
                if (parseInt(this.value) === delegateid) {
                    AddElement("modalcontainer", "errormodal", ErrorDialog("Delegate Exists", "You have already added this delegate to this course"));
                    delegateAdded = true;
                    return;
                }
            });
            if (delegateAdded)
                return;

            var response = post("/Checkout/SelectedDelegate", { delegateid: delegateid, scheduleid: jQuery('#dante #scheduleid').val(), bookingid: jQuery('#dante #bookingid').val(), rowindex: rowindex, cartitemid: jQuery("#dante #delegates" + rowindex + "cartitemid").val() });
            if (response.success) {
                jQuery("#dante #delegates" + rowindex).html(response.data.Delegate);
                DatePickers("#dante .delegate-form div[class*='datepicker'] > input");

                ComboBoxes("#dante .delegate-form .combobox");
                if (!delegateid) {
                    jQuery("#dante #delegates" + rowindex + " input[name$=FirstName], #dante #delegates" + rowindex + " input[name$=Surname]").keyup(function () {
                        return SelectDelegateSearch(this);
                    });
                    jQuery("#dante #delegates" + rowindex + " input[name$=FirstName], #dante #delegates" + rowindex + " input[name$=Surname]").focusout(function () {
                        SelectDelegatesCheckExists(this);
                    });
                }
            }
        },
        onSelectDelegates: function () {
            DeActive("btnselectdelegates");
            var formData = new FormData(jQuery("#dante #selectdelegatesform")[0]);
            jQuery.ajax(
                {
                    type: "POST",
                    url: BaseURL + "/Client/Checkout/SelectDelegates",
                    data: formData,
                    cache: false,
                    async: true,
                    headers: AuthToken != null ? { "X-AuthToken": AuthToken } : null,
                    crossOrigin: true,
                    processData: false,
                    contentType: false,
                    success: function (data) {
                        if (data.Success == true) {
                            if (jQuery("#dante #additionalschedules").val()) {
                                dante.loadSelectDelegates(scheduleindex = parseInt(jQuery("#dante #scheduleindex").val()) + 1, reload = true);
                            }
                            else dante.loadPayment();
                        }
                        if (data.Message != null && data.Message != "") {
                            if (document.getElementById("modalcontainer") != null)
                                AddElement("modalcontainer", "errormodal", ErrorDialog(data.MessageHeader ? data.MessageHeader : "", data.Message.replace(/(?:\r\n|\r|\n)/g, '<br>')));
                            else alert(data.Message);
                        }
                        Active("btnselectdelegates");
                        if (data != null && data.PartModified)
                            dante.loadSelectDelegates(null, true);
                    },
                    error: function (xhr, error, status) {
                        AddElement("modalcontainer", "errormodal", ErrorDialog("Error Occured", status));
                        Active("btnselectdelegates");
                    }
                });
        },
        /* /\ /\ SELECT DELEGATES /\ /\ */

        /* \/ \/ PAYMENT \/ \/ */
        loadPayment: function () {
            if (this.checkLogin("payment")) {
                load("/Checkout/Payment", { carttoken: CartToken }, "Payment");
                if (jQuery('a.paymentinitialise').length === 1) {//only one payment method
                    jQuery('a.paymentinitialise').trigger("click");
                }
            }
        },
        paymentInitialise: function (paymentgatewaytype, deposit, bookingtoken = null) {
            if (jQuery("#dante #paymentgatewaytype").val() == paymentgatewaytype)
                return;//This payment type already initialised

            switch (parseInt(paymentgatewaytype.replace("D", ""))) {
                case 0://account
                    paymentInitialiseAccount();
                    break;
                case 2://stripe
                    paymentInitialiseStripe(paymentgatewaytype, deposit, bookingtoken);
                    break;
            }

            jQuery("#dante #paymentgatewaytype").val(paymentgatewaytype);
            jQuery("#dante .note-payment-select.selected").removeClass("selected");
            jQuery("#dante #paymentgateway" + paymentgatewaytype).addClass("selected");
        },
        onPayment: function () {
            var paymentGatewayType = jQuery("#dante #paymentgatewaytype").val();
            if (paymentGatewayType == null || paymentGatewayType === "") {
                AddElement("modalcontainer", "errormodal", ErrorDialog("Payment", "Please select a payment type"));
                return;
            }
            if (jQuery("#dante #termsandconditions").length > 0 && !jQuery("#dante #termsandconditions").is(":checked")) {
                AddElement("modalcontainer", "errormodal", ErrorDialog("Payment", "Please check to confirm you agree to our terms and conditions"));
                return;
            }
            jQuery("#dante #paymentgateway" + paymentGatewayType + "form input[type=submit]").click();
        },
        loadConfirmation: function (bookingtoken, gatracking) {
            if (bookingtoken == null)
                bookingtoken = getParameterValue('bookingtoken');


            load("/Checkout/Confirmation", { bookingtoken: bookingtoken }, "Confirmation", false, urlparameters = [{ name: "bookingtoken", value: bookingtoken }]);

            if (gatracking) {
                //Send conversion
                var bookingItemRows = jQuery("#dante #confirmation-items tr");
                var bookingItems = [];

                for (var i = 0; i < bookingItemRows.length; i++) {
                    if (!jQuery(bookingItemRows[i]).data('id'))
                        continue;
                    var bookingItem = {};

                    bookingItem.item_name = jQuery(bookingItemRows[i]).find("td[data-header='Description']")[0].innerText;
                    var courseReference = jQuery(bookingItemRows[i]).data('coursereference');
                    if (courseReference)
                        bookingItem.item_id = courseReference;
                    else
                        bookingItem.item_id = jQuery(bookingItemRows[i]).find("td[data-header='Reference']")[0].innerText;


                    bookingItem.price = parseFloat(jQuery(bookingItemRows[i]).find("td[data-header='Total']").data('value'));
                    bookingItem.quantity = parseInt(jQuery(bookingItemRows[i]).find("td[data-header='Quantity']")[0].innerText);

                    bookingItems.push(bookingItem);
                }

                if (typeof gtag !== 'undefined') {//gtag defined, site doesn't use GTM, so let's send directly to Google Analytics
                    gtag("event", "purchase", {
                        transaction_id: jQuery('#dante #bookingreference')[0].innerText,
                        affiliation: 'Dante Checkout',
                        value: parseFloat(jQuery('#dante #bookingtotal').data('value')),
                        tax: parseFloat(jQuery('#dante #bookingtax').data('value')),
                        currency: jQuery('#dante #bookingcurrency').val(),
                        items: bookingItems
                    });
                }
                else {
                    dataLayer.push({
                        'event': 'purchase',
                        'ecommerce': {
                            'transaction_id': jQuery('#dante #bookingreference')[0].innerText,
                            'value': parseFloat(jQuery('#dante #bookingtotal').data('value')),                                  // Product Price- Type: Numeric
                            'tax': parseFloat(jQuery('#dante #bookingtax').data('value')),                              // Taxes If applicable- Type: Numeric
                            'currency': jQuery('#dante #bookingcurrency').val(),                     // Currency- Type: Numeric
                            'items': bookingItems
                        }
                    });
                }
            }

        },
        /* /\ /\ PAYMENT /\ /\ */

        /* \/ \/ ACCOUNT \/ \/ */

        loadAccount: function () {
            if (this.checkLogin("account")) {
                load("/Account", {}, "Account");
            }
        },

        /* /\ /\ ACCOUNT /\ /\ */

        /* \/ \/ BOOKINGS \/ \/ */

        loadBookings: function (reload = false) {
            if (this.checkLogin("bookings")) {
                load("/Account/Bookings", { startdate: jQuery('#dante #startdate').val(), enddate: jQuery('#dante #enddate').val() }, "Bookings", reload);
                DatePickers("#dante div[class*='datepicker'] > input");
            }
        },
        loadBooking: function (bookingtoken, reload = false) {
            if (bookingtoken == null)
                bookingtoken = getParameterValue('bookingtoken');

            if (this.checkLogin("booking")) {
                load("/Account/Booking", { bookingtoken: bookingtoken }, "Booking", reload, urlparameters = [{ name: "bookingtoken", value: bookingtoken }]);
            }
        },
        onBookingPayment: function () {
            jQuery('#paymentmodal').show();
            if (jQuery('a.paymentinitialise').length === 1) {//only one payment method
                jQuery('a.paymentinitialise').trigger("click");
            }
        },

        onDocumentGenerate: function (type, token) {
            DeActive("btndownloaddocument");
            var response = post("/Account/DocumentGenerate", { type: type, token: token });
            Active("btndownloaddocument");
            if (response.success) {
                window.location.href = BaseURL + "/Client/Account/DocumentDownload?filename=" + response.data.FileName + "&guid=" + response.data.GUID;
            }
        },

        /* /\ /\ BOOKINGS /\ /\ */

        /* \/ \/ TRAINING HISTORY \/ \/ */

        loadTrainingHistory: function (reload = false) {
            if (this.checkLogin("traininghistory")) {
                load("/Account/TrainingHistory", { startdate: jQuery('#dante #startdate').val(), enddate: jQuery('#dante #enddate').val(), firstname: jQuery('#dante #firstname').val(), surname: jQuery('#dante #surname').val() }, "TrainingHistory", reload);
                DatePickers("#dante div[class*='datepicker'] > input");
            }
        },

        /* /\ /\ TRAINING HISTORY /\ /\ */

        /* \/ \/ COURSES \/ \/ */

        loadCourses: function () {
            if (this.checkLogin("courses")) {
                load("/Account/Courses", null, "Courses", false);
            }
        },

        /* /\ /\ COURSES /\ /\ */

        /* \/ \/ SCHEDULE DELEGATES \/ \/ */

        loadScheduleDelegates: function (scheduledelegatetoken, reload = false) {
            if (scheduledelegatetoken == null)
                scheduledelegatetoken = getParameterValue('scheduledelegatetoken');

            if (this.checkLogin("scheduledelegates")) {
                load("/Account/ScheduleDelegates", { scheduledelegatetoken: scheduledelegatetoken }, "ScheduleDelegates", reload, urlparameters = [{ name: "scheduledelegatetoken", value: scheduledelegatetoken }]);
                DatePickers("#dante .delegate-form div[class*='datepicker'] > input");
                ComboBoxes("#dante .delegate-form .combobox");
                jQuery("#dante input[name$=FirstName], #dante input[name$=Surname]").keyup(function () {
                    SelectDelegateSearch(this);
                });

                jQuery("#dante input[name$=FirstName], #dante input[name$=Surname]").focusout(function () {
                    SelectDelegatesCheckExists(this);
                });

            }
        },

        onScheduleDelegates: function () {
            DeActive("btnupdatedelegates");
            var response = post("/Account/ScheduleDelegates", jQuery("#dante #scheduledelegatesform").serialize());
            if (response.success == true) {
                this.loadScheduleDelegates(null, true);
            }
            Active("btnupdatedelegates");

            if (response.data != null && response.data.PartModified)
                this.loadScheduleDelegates(null, true);

        },

        /* /\ /\ SCHEDULE DELEGATES /\ /\ */




        /* \/ \/ STORAGE \/ \/ */

        loadStorage: function (storagetoken) {
            if (storagetoken == null)
                storagetoken = getParameterValue('storagetoken');
            if (this.checkLogin("storage")) {
                load("/Account/Storage", { storagetoken: storagetoken }, "Storage", false, urlparameters = [{ name: "storagetoken", value: storagetoken }]);
            }
        },


        /* /\ /\ STORAGE /\ /\ */


        /* \/ \/ PROFILE \/ \/ */

        loadProfile: function () {
            if (this.checkLogin("profile")) {
                load("/Account/Profile", { carttoken: CartToken }, "Profile", false);
                DatePickers("#profileform div[class*='datepicker'] > input");
                ComboBoxes("#profileform .combobox");
            }
        },
        onProfile: function () {
            DeActive("btnprofile");
            var response = post("/Account/Profile", jQuery("#dante #profileform").serialize());
            if (response.success) {
                this.loadAccount();
            }
            Active("btnprofile");
        },

        /* /\ /\ PROFILE /\ /\ */

        addCallBack: function (name, fn) {
            CallBacks.push({ function: fn, name: name });
        }
    }

    function loginSetup() {
        var mInputElements = document.querySelectorAll("input[class*=login]");
        if (mInputElements != null)
            for (var i = 0; i < mInputElements.length; i++) {
                var mElement = mInputElements[i];
                if (mElement.value != "") {
                    var mLabelElement = mElement.previousElementSibling;
                    if (mLabelElement != null && mLabelElement.classList.contains("highlight") == false)
                        mLabelElement.classList.add("highlight");
                }

                var mLabelElement = mElement.previousElementSibling;
                mLabelElement.addEventListener('click', function (e) {
                    var mSiblingElement = e.target.nextElementSibling;
                    if (mSiblingElement != null)
                        mSiblingElement.focus();
                });

                mElement.addEventListener('focus', function (e) {
                    var mParentElement = e.target.parentNode;
                    if (mParentElement != null && mParentElement.classList.contains("focused") == false)
                        mParentElement.classList.add("focused");

                    var mSiblingElement = e.target.previousElementSibling;
                    if (mSiblingElement != null && mSiblingElement.classList.contains("highlight") == false)
                        mSiblingElement.classList.add("highlight");
                });

                mElement.addEventListener('blur', function (e) {
                    var mParentElement = e.target.parentNode;
                    if (mParentElement != null && mParentElement.classList.contains("focused") == true)
                        mParentElement.classList.remove("focused");

                    if (e.target.value == "") {
                        var mSiblingElement = e.target.previousElementSibling;
                        if (mSiblingElement != null && mSiblingElement.classList.contains("highlight") == true)
                            mSiblingElement.classList.remove("highlight");
                    }
                });
            }
        jQuery('#dante #registeremailaddress').keyup(function (event) {
            if (event.keyCode === 13) {//enter
                dante.onLoginCheckEmailExists();
                event.preventDefault();
            }
        });
    }

    function paymentInitialiseStripe(paymentgatewaytype, deposit, bookingtoken) {

        var response;
        if (bookingtoken != null)
            response = post("/Account/PaymentInitialise", { bookingtoken: bookingtoken, paymentgatewaytype: paymentgatewaytype.replace("D", ""), deposit: deposit });
        else
            response = post("/Checkout/PaymentInitialise", { carttoken: CartToken, paymentgatewaytype: paymentgatewaytype.replace("D", ""), deposit: deposit });

        if (response.success) {

            var stripe = Stripe(response.data.PublicKey);

            const appearance = {};
            const options = { /* options */ };
            const elements = stripe.elements({ clientSecret: response.data.ClientSecret, appearance });
            const paymentElement = elements.create('payment', options);
            paymentElement.mount("#dante #paymentgateway" + paymentgatewaytype + "card");

            jQuery("#paymentgateway" + paymentgatewaytype + "form")[0].addEventListener("submit", function (ev) {
                ev.preventDefault();
                DeActive("btncompletebooking");

                if (bookingtoken == null && !checkCartModifiedDate(jQuery("#cartmodifieddate").val()))
                    return;

                stripe
                    .confirmPayment({
                        elements,
                        redirect: 'if_required',
                    })
                    .then(function (result) {
                        if (result.error) {
                            AddElement("modalcontainer", "errormodal", ErrorDialog("Payment Error", result.error.message));
                            Active("btncompletebooking");
                        }
                        else if (result.paymentIntent && result.paymentIntent.status === "succeeded") {
                            // The payment has succeeded
                            paymentValidate(paymentgatewaytype.replace("D", ""), bookingtoken);
                            return true;
                        }
                    });
            });
        }
    }

    function paymentInitialiseAccount() {
        jQuery("#paymentgateway0form")[0].addEventListener("submit", function (ev) {

            ev.preventDefault();
            DeActive("btncompletebooking");

            if (!checkCartModifiedDate(jQuery("#cartmodifieddate").val()))
                return;

            jQuery.ajax(
                {
                    type: "POST",
                    url: BaseURL + "/Client/Checkout/PaymentAccount",
                    data: jQuery("#paymentgateway0form").serialize(),
                    dataType: "json",
                    cache: false,
                    async: true,
                    headers: AuthToken != null ? { "X-AuthToken": AuthToken } : null,
                    crossOrigin: true,
                    success: function (data) {
                        handleDefaultPostResponse(data);
                        if (data.Success) {
                            dante.clearCart(true);
                            dante.loadConfirmation(data.BookingToken, jQuery('#dante #googleanalyticstrackconversions').val());
                        } else Active("btncompletebooking");
                    },
                    error: function (xhr, error, status) {
                        AddElement("modalcontainer", "errormodal", ErrorDialog("Error Occured", status));
                        Active("btncompletebooking");
                    }
                });
        });
    }

    function checkCartModifiedDate(date) {
        var match;
        jQuery.ajax(
            {
                type: "GET",
                data: { carttoken: CartToken },
                url: BaseURL + "/Client/Checkout/CartModifiedDate",
                dataType: "json",
                async: false,
                headers: AuthToken != null ? { "X-AuthToken": AuthToken } : null,
                crossOrigin: true,
                success: function (data) {
                    if (data != null && date != data) {
                        AddElement("modalcontainer", "errormodal", ErrorDialog("Error", "Cart items have updated, please refresh this page and try again"));
                        match = false;
                    }
                    else match = true;
                },
                error: function (xhr, error, status) {
                    AddElement("modalcontainer", "errormodal", ErrorDialog("Error", status));
                }
            });
        return match;
    }

    function paymentValidate(paymentgatewaytype, bookingtoken) {
        var attempts = 0;
        AddElement("modalcontainer", "informationmodal", InformationDialog("Payment Processing", "Please wait while we are process the payment for your order."), true);
        //Poll the server every second to check if payment is complete
        var paymentValidateInterval = setInterval(function () {
            attempts++;
            var response;
            if (bookingtoken != null)
                response = post("/Account/PaymentValidate", { bookingtoken: bookingtoken, paymentgatewaytype: paymentgatewaytype });
            else
                response = post("/Checkout/PaymentValidate", { carttoken: CartToken, paymentgatewaytype: paymentgatewaytype });
            if (response.success) {
                if (response.data.BookingToken != null) {
                    clearInterval(paymentValidateInterval);
                    dante.clearCart(true);
                    RemoveElement("informationmodal");
                    if (bookingtoken != null)
                        dante.loadBooking(bookingtoken, true);
                    else
                        dante.loadConfirmation(response.data.BookingToken, jQuery('#dante #googleanalyticstrackconversions').val());
                }
            }
            else if (attempts >= 20) {
                clearInterval(paymentValidateInterval);
                Active("btncompletebooking");
                AddElement("modalcontainer", "errormodal", ErrorDialog("Couldn't Validate Payment", "Unfortunately, we couldn't validate the payment for this booking."));
            }
        }, 1000);

        RemoveElement("informationmodal");
    }

    function load(url, data, action, reload = false, urlparameters = null) {
        var container = jQuery("#dantecontainer");
        if (container.hasClass("dante-" + action) && !reload)
            return;
        container.removeClass(function (index, css) {
            return (css.match(/\bdante-\S+/g) || []).join(' '); // removes anything that starts with "dante-"
        });
        container.addClass("dante-" + action);
        jQuery.ajax(
            {
                type: "GET",
                url: BaseURL + "/Client" + url,
                data: data,
                async: false,
                headers: AuthToken != null ? { "X-AuthToken": AuthToken } : null,
                success: function (data) {
                    jQuery("#dantecontainer").html(data);
                },
                error: function (xhr, error, status) {
                    AddElement("modalcontainer", "errormodal", ErrorDialog("Error Occured", status));
                }
            });

        var hash = "#/" + action;
        if (urlparameters != null && urlparameters.length > 0) {
            hash += "?"
            urlparameters.forEach(function (item) {
                hash += item.name + "=" + item.value + "&";
            })
            hash = hash.substring(0, hash.length - 1);//remove trailing &
        }
        window.location.hash = hash;

        //Ensure this is ran last
        var callBack = jQuery.grep(CallBacks, function (n, i) {
            return n.name == "load" + action;
        })

        if (callBack != null && callBack.length > 0)
            callBack[0].function();
    }

    function post(url, postdata) {
        var success = false;
        var outputData = null;

        jQuery.ajax(
            {
                type: "POST",
                url: BaseURL + "/Client" + url,
                data: postdata,
                dataType: "json",
                cache: false,
                async: false,
                headers: AuthToken != null ? { "X-AuthToken": AuthToken } : null,
                crossOrigin: true,
                success: function (data) {
                    returndata = data;

                    if (data.Message != null && data.Message != "") {
                        if (document.getElementById("modalcontainer") != null)
                            AddElement("modalcontainer", "errormodal", ErrorDialog(data.MessageHeader ? data.MessageHeader : "", data.Message.replace(/(?:\r\n|\r|\n)/g, '<br>')));
                        else alert(data.Message);
                    }

                    if (data.AuthToken != null) {
                        setCookie("dante-authtoken", data.AuthToken, 1);//Cookie expires in 24 hours
                        AuthToken = data.AuthToken
                    }

                    if (data.CartToken != null) {
                        setCookie("dante-carttoken", data.CartToken, 1);//Cookie expires in 24 hours
                        CartToken = data.CartToken
                    }

                    if (data.Success) {
                        success = true;
                    }
                    outputData = data;
                },
                error: function (xhr, error, status) {
                    AddElement("modalcontainer", "errormodal", ErrorDialog("Error Occured", status));
                }
            });
        return { data: outputData, success: success };
    }

    function handleDefaultPostResponse(data) {
        if (data.Message != null && data.Message != "")
            AddElement("modalcontainer", "errormodal", ErrorDialog(data.MessageHeader ? data.MessageHeader : "", data.Message.replace(/(?:\r\n|\r|\n)/g, '<br>')));

        if (data.AuthToken != null) {
            setCookie("dante-authtoken", data.AuthToken, 1);//Cookie expires in 24 hours
            AuthToken = data.AuthToken
        }

        if (data.CartToken != null) {
            setCookie("dante-carttoken", data.CartToken, 1);//Cookie expires in 24 hours
            CartToken = data.CartToken
        }
    }

    function getCookie(cname) {
        let name = cname + "=";
        let decodedCookie = decodeURIComponent(document.cookie);
        let ca = decodedCookie.split(";");
        for (let i = 0; i < ca.length; i++) {
            let c = ca[i];
            while (c.charAt(0) == " ") {
                c = c.substring(1);
            }
            if (c.indexOf(name) == 0) {
                return c.substring(name.length, c.length);
            }
        }
        return "";
    }

    function setCookie(cname, value, expirydays) {
        var exdate = new Date();
        exdate.setDate(exdate.getDate() + expirydays);
        var c_value = escape(value) + ((expirydays == null) ? "" : "; expires=" + exdate.toUTCString());
        document.cookie = cname + "=" + c_value + "; path=/";
    }

    function getParameterValue(pname) {
        var hash = window.location.hash;
        if (hash != null && hash.indexOf("?") != -1) {
            return (new URLSearchParams(hash.substring(hash.indexOf("?"), hash.length))).get(pname);
        }
        return null;
    }

    function loadScript(url) {
        if (jQuery("script[src*='" + url + "']").length == 0) {
            jQuery.ajax({
                cache: true,
                url: url,
                dataType: 'script',
                success: function () {
                    //console.log("Dante: Loaded JS: " + url);
                },
                error: function (xhr, error, status) {
                    console.error("Dante: Error loading " + url + ": " + status);
                }
            });
        }
    }

    function loadPage(action) {
        var hash = window.location.hash;

        if (action == null && hash != null) {
            if (hash.indexOf("?") != -1) {
                action = hash.substring(2, hash.indexOf("?"));
            }
            else action = hash.substring(2, hash.length);
        }

        if (jQuery("#dantecontainer").hasClass("dante-" + action))
            return;//Current page already loaded, continue

        switch (action.toLowerCase()) {
            case "login":
                if (dante.checkLogin()) {
                    dante.loadCart();
                }
                break;
            case "payment":
                dante.loadPayment();
                break;
            case "logout":
                dante.logout();
                dante.checkLogin();
                break;
            case 'selectdelegates':
                dante.loadSelectDelegates();
                break;
            case "confirmation":
                dante.loadConfirmation(null, false);
                break;
            case "resetpassword":
                dante.loadResetPasswordReturn();
                break;
            case "register":
                dante.loadRegister();
                break;
            case "account":
                dante.loadAccount();
                break;
            case "bookings":
                dante.loadBookings();
                break;
            case "booking":
                dante.loadBooking();
                break;
            case "traininghistory":
                dante.loadTrainingHistory();
                break;
            case "courses":
                dante.loadCourses();
                break;
            case "storage":
                dante.loadStorage();
                break;
            case "scheduledelegates":
                dante.loadScheduleDelegates(null);
                break;
            case "profile":
                dante.loadProfile();
                break;
            case "cart":
            default:
                dante.loadCart(false);
                break;
        }
    }

    function Initialise(action = null) {
        CartToken = getCookie("dante-carttoken");
        AuthToken = getCookie("dante-authtoken");
        BaseURL = jQuery("#dantebaseurl").val();
        if (BaseURL == null || BaseURL == "")
            console.error("Dante could not initialise: No hidden element with ID 'dantebaseurl' on page");

        if (jQuery("#dante").length == 0)
            console.error("Dante could not initialise: Div with ID 'dante' not setup");
        else {
            jQuery("#dante").append("<div id=\"dantecontainer\"></div>");
            jQuery("#dante").append("<div id=\"modalcontainer\"></div>");
        }

        loadScript(BaseURL + "/Scripts/moment.min.js");
        loadScript("https://js.stripe.com/v3");
        loadScript(BaseURL + "/Scripts/daterangepicker.js");
        AddClickOffEvent();
        loadPage(action);
    }

    function AddClickOffEvent() {
        window.addEventListener('click', function (e) {
            var mElement = document.elementFromPoint(e.x, e.y);
            if (mElement != null) {
                if (mElement.tagName === "INPUT" || mElement.tagName === "SPAN" || mElement.tagName === "UL" || mElement.tagName === "LI")
                    return;

                jQuery("#dante div.simplesearch").remove();
                jQuery("#dante div.combobox ul").removeClass("active");
            }
        }, true);
    }

    function SelectDelegateSearch(element) {
        clearTimeout(timeout);
        timeout = setTimeout(function () {
            var delegateForm = jQuery(element).parent().parent().parent();
            var firstName = jQuery(delegateForm).find("input[name$=FirstName]").val()
            var surname = jQuery(delegateForm).find("input[name$=Surname]").val()
            var scheduleID = jQuery("#dante #scheduleid").val();

            jQuery("#dante div.simplesearch").remove();


            var mRootElement = document.createElement("div");
            mRootElement.classList.add("d-col-24", "pr200", "simplesearch");
            mRootElement.setAttribute("style", "position: absolute; z-index: 1; top: 59px;");

            if (scheduleID != null && (element.value.length > 3)) {//Make sure we haven't left the select delegates page
                jQuery.ajax(
                    {
                        type: "POST",
                        url: BaseURL + "/Client/Checkout/selectDelegatesSearch",
                        data: { 'carttoken': CartToken, 'scheduleid': scheduleID, 'firstname': firstName, 'surname': surname },
                        dataType: "json",
                        cache: false,
                        async: true,
                        crossOrigin: true,
                        headers: AuthToken != null ? { "X-AuthToken": AuthToken } : null,
                        success: function (data) {
                            if (data.Success && data.Delegates.length > 0 && scheduleID == jQuery("#dante #scheduleid").val()) {
                                var mULElement = document.createElement("ul");
                                jQuery.each(data.Delegates, function (index, value) {

                                    if (jQuery('#dante input[name$=ID]').filter('[value=' + value.ID + ']').length == 0) {//Don't add existing delegate
                                        var mLIElement = document.createElement("li");
                                        mLIElement.classList.add("flex", "flex-vc");
                                        mLIElement.setAttribute("style", "height: 30px;");

                                        var mSpanElement = document.createElement("span");
                                        mSpanElement.classList.add("flex", "d-col-24", "foreground-dark");
                                        mSpanElement.setAttribute("data-id", value.ID);
                                        mSpanElement.setAttribute("onclick", "return dante.OnSelectedDelegate(" + jQuery(delegateForm).find("input[name$=RowIndex]").val() + ", " + value.ID + "); ");
                                        mSpanElement.textContent = value.FirstName + " " + value.Surname
                                        mSpanElement.setAttribute("style", "cursor: pointer;");

                                        mLIElement.appendChild(mSpanElement);
                                        mULElement.appendChild(mLIElement);
                                    }
                                });
                                mRootElement.appendChild(mULElement);
                                element.parentNode.insertAdjacentElement('beforeend', mRootElement);
                            }
                        },
                        error: function (xhr, error, status) {

                        }
                    });
            }
        }, 250);

        return false;
    }


    function SelectDelegatesCheckExists(element) {
        clearTimeout(timeout);//Delay slightly, just in case delegate has selected a person in this time
        timeout = setTimeout(function () {
            if (jQuery("#dantecontainer").hasClass("dante-SelectDelegates")) {//Make sure we haven't left the select delegates page
                var delegateForm = jQuery(element).parent().parent().parent();
                var firstName = jQuery(delegateForm).find("input[name$=FirstName]").val()
                var surname = jQuery(delegateForm).find("input[name$=Surname]").val()
                var delegateID = jQuery(delegateForm).find("input[name$=ID]").val()
                var scheduleID = jQuery("#dante #scheduleid").val();


                if (!delegateID && firstName && surname && firstName.length > 2 && surname.length > 3) {
                    jQuery.ajax(
                        {
                            type: "POST",
                            url: BaseURL + "/Client/Checkout/selectDelegatesSearch",
                            data: { 'carttoken': CartToken, 'scheduleid': scheduleID, 'firstname': firstName, 'surname': surname },
                            dataType: "json",
                            cache: false,
                            async: true,
                            crossOrigin: true,
                            headers: AuthToken != null ? { "X-AuthToken": AuthToken } : null,
                            success: function (data) {
                                if (data.Success && data.Delegates.length > 0 && scheduleID == jQuery("#dante #scheduleid").val()) {
                                    data.Delegates.forEach(function (delegate) {
                                        if (delegate.FirstName.toLowerCase() == firstName.toLowerCase() && delegate.Surname.toLowerCase() == surname.toLowerCase()) {//exact name match
                                            if (jQuery('#dante input[name$=ID]').filter('[value=' + delegate.ID + ']').length == 0) {
                                                AddElement("modalcontainer", "confirmmodal",
                                                    ConfirmDialog("Delegate Already Exists", "A record for " + delegate.FirstName + ' ' + delegate.Surname + ' already exists. Would you like to use this record instead of creating a new delegate?<br/><br/>This will help to avoid duplicates in future training and bookings',
                                                        '<button class="button-command" onclick="dante.OnSelectedDelegate(' + jQuery(delegateForm).find("input[name$=RowIndex]").val() + ',' + delegate.ID + '); return false;" id="btnconfirm">Yes, use existing delegate</button>'));
                                            }
                                        }
                                    })

                                }
                            },
                            error: function (xhr, error, status) {

                            }
                        });
                }
            }
        }, 250);
    }

    /* \/ \/ ELEMENTS \/ \/ */
    function AddElement(parentid, elementid, element, remove = true) {
        if (elementid != null && remove == true)
            RemoveElement(elementid);

        var mElement = document.getElementById(parentid);
        if (mElement != null) {
            mElement.insertAdjacentElement('beforeend', element);
            ShowElement(elementid);
        }
    }

    function RemoveElement(id) {
        var mElement = document.getElementById(id);
        if (mElement != null)
            mElement.parentNode.removeChild(mElement);
    }

    function ShowElement(id) {
        var mElement = document.getElementById(id);
        if (mElement != null)
            if (mElement.style.display === 'none' || mElement.style.display === '')
                mElement.style.display = 'block';
    }

    function FindElementByAttribute(element, attribute) {
        var mElement = null;

        if (element.hasAttribute(attribute) == true)
            mElement = element;
        else {
            if (mElement == null && element.parentNode != null) {
                var mTempElement = FindElementByAttribute(element.parentNode, attribute);
                if (mTempElement != null)
                    mElement = mTempElement;
            }
        }

        return mElement;
    }
    /* /\ /\ ELEMENTS /\ /\ */

    /* \/ \/ CONTROLS \/ \/ */
    function DatePickers(value) {
        var mElements = document.querySelectorAll(value);
        if (mElements != null) {
            try {
                for (var i = 0; i < mElements.length; i++) {
                    var mValue = mElements[i].value;
                    jQuery('#' + mElements[i].id).daterangepicker({ locale: { "format": "DD/MM/YYYY", "firstDay": 1 }, opens: "center", drops: "up", singleDatePicker: true, showDropdowns: true, minYear: mElements[i].id.includes("dateofbirth") ? 1900 : new Date().getFullYear() - 5, maxYear: new Date().getFullYear() + 3, autoApply: true, autoUpdateInput: false });
                    jQuery('#' + mElements[i].id).on('apply.daterangepicker', function (ev, picker) { jQuery(this).val(picker.startDate.format('DD/MM/YYYY')); });
                    jQuery('#' + mElements[i].id).on('cancel.daterangepicker', function (ev, picker) { jQuery(this).val(''); });

                    if (mValue == "")
                        mElements[i].value = "";
                }
            }
            catch (ex) {
                //had this occur a few times in testing due to moment.js being loaded dynamically so not being ready at the point of loading. Only tends to happen when the page is refreshed
                console.error("Dante error loading date pickers: " + ex.message);
            }
        }
    }

    function ComboBoxes(value) {
        var mComboboxElements = document.querySelectorAll(value);
        if (mComboboxElements != null)
            for (var A = 0; A < mComboboxElements.length; A++) {
                var mSpanElement = mComboboxElements[A].querySelector("span");
                if (mSpanElement != null) {
                    mSpanElement.addEventListener('click', function (e) {
                        event.stopPropagation();
                        var mDataDisabled = e.target.getAttribute('data-disabled');
                        if (mDataDisabled == null || (mDataDisabled != null && mDataDisabled == "false")) {
                            var mDataSearch = e.target.getAttribute('data-search');
                            var mULElement = e.target.parentNode.querySelector('ul');
                            if (mULElement != null) {
                                if (mULElement.classList.contains('active') == true) {
                                    mULElement.classList.remove('active');

                                    if (mDataSearch != null) {
                                        var mInputSearchElement = document.getElementById(mDataSearch);
                                        if (mInputSearchElement != null) {
                                            mInputSearchElement.autofocus = false;
                                            mInputSearchElement.style.display = "none";
                                        }
                                    }
                                    return;
                                }

                                if (mDataSearch != null) {
                                    var mInputElement = document.getElementById(mDataSearch);
                                    if (mInputElement != null) {
                                        mInputElement.style.display = "block";
                                        mInputElement.focus();
                                        mInputElement.autofocus = true;
                                    }
                                }

                                mULElement.classList.add('active');

                                if (mULElement.classList.contains('list-full-text-width') == false) {
                                    var mLIElements = mULElement.querySelectorAll("li");
                                    if (mLIElements != null) {
                                        for (var B = 0; B < mLIElements.length; B++)
                                            if (mLIElements[B].innerText.length > mULElement.clientWidth / 10) {
                                                if (mULElement.classList.contains('list-full-text-width') == false)
                                                    mULElement.classList.add('list-full-text-width');

                                                break;
                                            }
                                    }
                                }
                            }
                        }
                    });
                }

                var mSearchElement = mComboboxElements[A].querySelector("input.search");
                if (mSearchElement != null && mSearchElement.getAttribute("data-list") != null) {
                    mSearchElement.onkeyup = function (e) {
                        clearTimeout(timeout);
                        timeout = setTimeout(function () {
                            var mListID = e.target.getAttribute('data-list');
                            if (e.target.value.length != 0) {
                                if (e.target.value.length >= 1)
                                    UpdateSearch(e.target.value, mListID);
                                else
                                    ResetSearch(mListID);
                            }
                            else
                                ResetSearch(mListID);
                        }, e, 150);
                    };
                }

                var mULElement = mComboboxElements[A].querySelector("ul");
                if (mULElement != null) {
                    mULElement.addEventListener('click', function (e) {
                        event.stopPropagation();

                        if (e.target.tagName === 'LI') {
                            var mLIElement = this.querySelector('li.selected');
                            if (mLIElement != null)
                                mLIElement.classList.remove('selected');

                            e.target.classList.add('selected');

                            var mInputHiddenElement = this.parentNode.querySelector("input[type='hidden']");
                            if (mInputHiddenElement != null) {
                                mInputHiddenElement.value = e.target.getAttribute('data-id');

                                if (mInputHiddenElement.hasAttribute('onchange') == true)
                                    mInputHiddenElement.onchange();

                                var mULElement = this.parentNode.querySelector('ul');
                                if (mULElement != null)
                                    mULElement.classList.remove('active');

                                var mDataSearch = mULElement.getAttribute('data-search');
                                if (mDataSearch != null) {
                                    var mInputSearchElement = document.getElementById(mDataSearch);
                                    if (mInputSearchElement != null) {
                                        mInputSearchElement.autofocus = false;
                                        mInputSearchElement.style.display = "none";
                                    }
                                }

                                var mSpanElement = this.parentNode.querySelector('span');
                                if (mSpanElement != null) {
                                    var mDataValue = e.target.getAttribute('data-value');
                                    if (mDataValue == null)
                                        mSpanElement.innerHTML = e.target.innerHTML;
                                    else
                                        mSpanElement.innerHTML = mDataValue;
                                }
                            }
                        }
                    });
                }
            }
    }

    function UpdateSearch(text, elementid) {
        var mULElement = document.getElementById(elementid);
        if (mULElement != null)
            if (mULElement.classList.contains('active') == false)
                mULElement.classList.add('active');

        var mLIElements = document.querySelectorAll("#" + elementid + " li");
        if (mLIElements != null)
            for (var A = 0; A < mLIElements.length; A++) {
                var mText = mLIElements[A].innerText.toLowerCase();
                if (mText.indexOf(text.toLowerCase()) !== -1)
                    mLIElements[A].style.display = "";
                else
                    mLIElements[A].style.display = "none";
            }
    }

    function ResetSearch(elementid) {
        var mElements = document.querySelectorAll("#" + elementid + " li");
        if (mElements != null)
            for (var A = 0; A < mElements.length; A++)
                mElements[A].style.display = "";
    }
    /* /\ /\ CONTROLS /\ /\ */

    /* \/ \/ DIALOGS \/ \/ */
    function ErrorDialog(title, message) {
        var mElement;
        var mRootElement = document.createElement("div");
        mRootElement.id = "errormodal";
        mRootElement.classList.add("dante-modal");
        mRootElement.setAttribute("style", "display: block;");

        var mRootContainerElement = document.createElement("div");
        mRootContainerElement.classList.add("dante-modal-container", "d-col-xs-20", "d-col-sm-14", "d-col-md-12", "d-col-lg-10", "d-col-xl-6");

        /* \/ \/ HEADER \/ \/ */
        var mHeaderElement = document.createElement("div");
        mHeaderElement.classList.add("flex-nowrap-row", "flex-vc", "flex-space-between", "d-col-24", "dante-modal-header");
        mHeaderElement.setAttribute("style", "background-color: rgb(107,17,17);");

        mElement = document.createElement("span");
        mElement.classList.add("dante-modal-title", "foreground-light");
        mElement.innerHTML = '<span class="fa fa-fw fa-exclamation-triangle fs150 mr100" style="color: rgb(255,0,0)"></span>' + title;
        mHeaderElement.appendChild(mElement);
        mRootContainerElement.appendChild(mHeaderElement);
        /* /\ /\ HEADER /\ /\ */

        /* \/ \/ CONTENT \/ \/ */
        var mContentElement = document.createElement("div");
        mContentElement.classList.add("dante-modal-content");

        mElement = document.createElement("p");
        mElement.classList.add("p100");
        mElement.innerHTML = message;
        mContentElement.appendChild(mElement);
        mRootContainerElement.appendChild(mContentElement);
        /* /\ /\ CONTENT /\ /\ */

        /* \/ \/ BUTTON(S) \/ \/ */
        var mButtonContainerElement = document.createElement("div");
        mButtonContainerElement.classList.add("flex-nowrap-row", "d-col-24", "flex-vc", "flex-end", "mt200");

        mElement = document.createElement("button");
        mElement.classList.add("button-command", "fuc", "mr100");
        mElement.setAttribute("onclick", "jQuery('#errormodal').hide(); return false;");
        mElement.innerText = "ok";
        mButtonContainerElement.appendChild(mElement);
        mRootContainerElement.appendChild(mButtonContainerElement);
        /* /\ /\ BUTTON(S) /\ /\ */

        mRootElement.appendChild(mRootContainerElement);

        return mRootElement;
    }

    function InformationDialog(title, message, typeid = 0) {
        var mElement;
        var mIconRGB = "color: rgb(255,255,255);";
        var mBackgroundRGB = "background-color: rgb(0,0,0);";

        switch (typeid) {
            //Information
            case 1:
                {
                    mIconRGB = "color: rgb(0,255,0);";
                    mBackgroundRGB = "background-color: rgb(45,128,45);";
                }
                break;

            // Warning
            case 2:
                {
                    mIconRGB = "color: rgb(244,200,0);";
                    mBackgroundRGB = "background-color: rgb(244,123,0);";
                }
                break;
        }

        var mRootElement = document.createElement("div");
        mRootElement.id = "informationmodal";
        mRootElement.classList.add("dante-modal");
        mRootElement.setAttribute("style", "display: block;");

        var mRootContainerElement = document.createElement("div");
        mRootContainerElement.classList.add("dante-modal-container", "d-col-xs-20", "d-col-sm-14", "d-col-md-12", "d-col-lg-10", "d-col-xl-6");

        /* \/ \/ HEADER \/ \/ */
        var mHeaderElement = document.createElement("div");
        mHeaderElement.classList.add("flex-nowrap-row", "flex-vc", "flex-space-between", "d-col-24", "dante-modal-header");
        mHeaderElement.setAttribute("style", mBackgroundRGB);

        mElement = document.createElement("span");
        mElement.classList.add("dante-modal-title", "foreground-light");
        mElement.innerHTML = '<span class="fa fa-fw fa-info-circle fs150 mr100" style="' + mIconRGB + '"></span>' + title;
        mHeaderElement.appendChild(mElement);

        mElement = document.createElement("span");
        mElement.classList.add("modal-close", "foreground-light");
        mElement.setAttribute("onclick", "jQuery('#informationmodal').hide(); return false;");
        mHeaderElement.appendChild(mElement);

        mRootContainerElement.appendChild(mHeaderElement);
        /* /\ /\ HEADER /\ /\ */

        /* \/ \/ CONTENT \/ \/ */
        var mContentElement = document.createElement("div");
        mContentElement.classList.add("dante-modal-content");

        mElement = document.createElement("p");
        mElement.classList.add("p100");
        mElement.innerHTML = message;
        mContentElement.appendChild(mElement);
        mRootContainerElement.appendChild(mContentElement);
        /* /\ /\ CONTENT /\ /\ */

        /* \/ \/ BUTTON(S) \/ \/ */
        var mButtonContainerElement = document.createElement("div");
        mButtonContainerElement.classList.add("flex-nowrap-row", "d-col-24", "flex-vc", "flex-end", "mt200");

        mElement = document.createElement("button");
        mElement.classList.add("button-command", "fuc", "mr100");
        mElement.setAttribute("onclick", "jQuery('#informationmodal').hide(); return false;");
        mElement.innerText = "ok";
        mButtonContainerElement.appendChild(mElement);
        mRootContainerElement.appendChild(mButtonContainerElement);
        /* /\ /\ BUTTON(S) /\ /\ */

        mRootElement.appendChild(mRootContainerElement);

        return mRootElement;
    }

    function ConfirmDialog(title, message, action) {
        var mElement;
        var mRootElement = document.createElement("div");
        mRootElement.id = "confirmmodal";
        mRootElement.classList.add("dante-modal");
        mRootElement.setAttribute("style", "display: block;");

        var mRootContainerElement = document.createElement("div");
        mRootContainerElement.classList.add("dante-modal-container", "d-col-xs-20", "d-col-sm-14", "d-col-md-12", "d-col-lg-10", "d-col-xl-6");
        //mRootElement.setAttribute("style", "display: flex; flex-direction: column;");

        /* \/ \/ HEADER \/ \/ */
        var mHeaderElement = document.createElement("div");
        mHeaderElement.classList.add("flex-nowrap-row", "flex-vc", "flex-space-between", "d-col-24", "dante-modal-header", "background-default");

        mElement = document.createElement("span");
        mElement.classList.add("dante-modal-title");
        mElement.innerHTML = '<span class="dante-modal-title"><span class="fa fa-fw fa-warning fs150 mr100 dante-modal-icon"></span>' + title;
        mHeaderElement.appendChild(mElement);

        mElement = document.createElement("span");
        mElement.classList.add("dante-modal-close");
        mElement.setAttribute("onclick", "jQuery('#confirmmodal').hide(); return false;");
        mElement.innerHTML = "<i>x</i>";
        mHeaderElement.appendChild(mElement);

        mRootContainerElement.appendChild(mHeaderElement);
        /* /\ /\ HEADER /\ /\ */

        /* \/ \/ CONTENT \/ \/ */
        var mContentElement = document.createElement("div");
        mContentElement.classList.add("flex-wrap-column", "p100");

        mElement = document.createElement("p");
        mElement.innerHTML = message;
        mContentElement.appendChild(mElement);

        /* \/ \/ BUTTON(S) \/ \/ */
        var mButtonContainerElement = document.createElement("div");
        mButtonContainerElement.classList.add("flex-nowrap-row", "d-col-24", "flex-end", "mt100");
        mButtonContainerElement.innerHTML = action;
        mContentElement.appendChild(mButtonContainerElement);
        /* /\ /\ BUTTON(S) /\ /\ */


        mRootContainerElement.appendChild(mContentElement);
        /* /\ /\ CONTENT /\ /\ */

        mRootElement.appendChild(mRootContainerElement);
        return mRootElement;
    }

    function TermsAndConditionsDialog(title, message) {
        var mElement;
        var mRootElement = document.createElement("div");
        mRootElement.id = "termsandconditionsmodal";
        mRootElement.classList.add("dante-modal");
        mRootElement.setAttribute("style", "overflow: hidden; display: block;");

        var mRootContainerElement = document.createElement("div");
        mRootContainerElement.classList.add("dante-modal-container", "d-col-18", "d-col-xs-20", "d-col-sm-16", "d-col-md-16");
        mRootContainerElement.setAttribute("style", "display: flex; flex-direction: column; max-height: 80%;");

        /* \/ \/ HEADER \/ \/ */
        var mHeaderElement = document.createElement("div");
        mHeaderElement.classList.add("flex-nowrap-row", "flex-vc", "flex-space-between", "d-col-24", "dante-modal-header", "background-default");

        mElement = document.createElement("span");
        mElement.classList.add("dante-modal-title");
        mElement.innerHTML = title;
        mHeaderElement.appendChild(mElement);

        mElement = document.createElement("span");
        mElement.classList.add("dante-modal-close");
        mElement.innerHTML = "<i>x</i>";
        mElement.setAttribute("onclick", "jQuery('#termsandconditionsmodal').hide(); return false;");
        mHeaderElement.appendChild(mElement);

        mRootContainerElement.appendChild(mHeaderElement);
        /* /\ /\ HEADER /\ /\ */

        /* \/ \/ CONTENT \/ \/ */
        var mContentElement = document.createElement("div");
        mContentElement.setAttribute("style", "overflow-y: auto;");

        mElement = document.createElement("span");
        mElement.classList.add("p200");
        mElement.innerHTML = message;
        mContentElement.appendChild(mElement);
        mRootContainerElement.appendChild(mContentElement);
        /* /\ /\ CONTENT /\ /\ */

        /* \/ \/ FOOTER \/ \/ */
        var mFooterElement = document.createElement("div");
        mFooterElement.classList.add("flex-nowrap-row", "flex-end", "d-col-24", "mt100");

        mElement = document.createElement("button");
        mElement.classList.add("button", "button-large", "button-default");
        mElement.innerText = "Agree and Close";
        mElement.setAttribute("onclick", "jQuery('#termsandconditionsmodal').hide(); return false;");
        mFooterElement.appendChild(mElement);
        mRootContainerElement.appendChild(mFooterElement);

        /* /\ /\ FOOTER /\ /\ */

        mRootElement.appendChild(mRootContainerElement);


        return mRootElement;
    }

    function DeActive(elementid) {
        var mElement = document.getElementById(elementid);
        if (mElement != null && !mElement.disabled) {
            mElement.disabled = true;
            mElement.innerHTML = "<i class=\"fa fa-fw fa-spin fa-refresh mr50\"></i>" + mElement.innerHTML;
        }
    }

    function Active(elementid) {
        var mElement = document.getElementById(elementid);
        if (mElement != null) {
            mElement.disabled = false;
            mElement.innerHTML = mElement.innerText;
        }
    }
    /* /\ /\ DIALOGS /\ /\ */
})(jQuery);

document.addEventListener("DOMContentLoaded", function (event) {
    dante.setup();
});
