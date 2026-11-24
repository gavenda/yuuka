package dev.gavenda.yuuka.ui.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Security
import dev.gavenda.yuuka.data.model.SecurityKinds
import dev.gavenda.yuuka.data.model.Trade
import dev.gavenda.yuuka.domain.Holding
import dev.gavenda.yuuka.domain.TradeKinds
import dev.gavenda.yuuka.domain.UNIT
import dev.gavenda.yuuka.domain.boardLot
import dev.gavenda.yuuka.domain.currencySymbol
import dev.gavenda.yuuka.domain.dividendTax
import dev.gavenda.yuuka.domain.isFund
import dev.gavenda.yuuka.domain.parseMoney
import dev.gavenda.yuuka.domain.parsePrice
import dev.gavenda.yuuka.domain.parseUnits
import dev.gavenda.yuuka.domain.positions
import dev.gavenda.yuuka.domain.priceInput
import dev.gavenda.yuuka.domain.pseFees
import dev.gavenda.yuuka.domain.toDecimalString
import dev.gavenda.yuuka.domain.tradeGross
import dev.gavenda.yuuka.domain.unitsFor
import dev.gavenda.yuuka.domain.unitsHeldBefore
import dev.gavenda.yuuka.domain.unitsInput
import dev.gavenda.yuuka.domain.valueOf
import dev.gavenda.yuuka.ui.common.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val KINDS = listOf(TradeKinds.BUY, TradeKinds.SELL, TradeKinds.DIVIDEND)

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/** The time of day an `occurredOn` names, or null when it names only the day. */
internal fun timeOf(occurredOn: String): LocalTime? =
    if (occurredOn.length > 10) runCatching { LocalTime.parse(occurredOn.substring(11, 16)) }.getOrNull() else null

/**
 * A buy, a sell or a cash dividend of a PSE stock, or a subscription, a redemption or a distribution of a UITF, as a
 * full-screen form. The security comes first and fills in the account that holds it and its last close or NAVPU.
 *
 * For a stock, the fees a PSE broker charges (or a dividend's final tax) are worked out as the shares and price are
 * typed, so what the trade costs or brings in is known before it is saved. A fund is bought for an amount, as a
 * trust bank sells it: the amount and the NAVPU fill in the units, which stay editable to match the confirmation,
 * and the amount is the cash. Its fees, if any, are typed. Given [editing], it opens on that trade and puts it
 * right, its cash leg with it.
 */
@Composable
fun TradeForm(
    state: InvestmentsUiState,
    onSave: suspend (TradeSubmission) -> Unit,
    onDismiss: () -> Unit,
    editing: Trade? = null,
    initialSymbol: String? = null,
    initialAccount: String? = null,
) {
    val busy = rememberBusyState()
    val snackbarHostState = LocalSnackbarHostState.current
    val savedMessage = stringResource(if (editing != null) R.string.investments_trade_updated else R.string.investments_trade_added)
    val editingFund = editing != null && isFund(editing.symbol)

    var kind by remember { mutableStateOf(editing?.kind ?: TradeKinds.BUY) }
    var symbol by remember { mutableStateOf(editing?.symbol ?: initialSymbol) }
    var account by remember { mutableStateOf(editing?.accountId ?: initialAccount ?: state.brokerCash.keys.firstOrNull()) }
    var date by remember { mutableStateOf(editing?.let { LocalDate.parse(it.occurredOn.take(10)) } ?: LocalDate.now()) }
    // A trade entered before times were kept has none, and stays without one unless it is given one.
    var time by remember {
        mutableStateOf(if (editing != null) timeOf(editing.occurredOn) else LocalTime.now().withSecond(0).withNano(0))
    }
    var units by remember { mutableStateOf(editing?.let { unitsInput(it.units) } ?: "") }
    var price by remember {
        mutableStateOf(
            editing?.let { priceInput(it.price) }
                ?: initialSymbol?.let { state.securitiesBySymbol[it] }?.let { priceInput(it.price) }
                ?: "",
        )
    }
    // A fund's subscription or redemption: the cash, as the trust bank said it.
    var amount by remember {
        mutableStateOf(
            if (editing != null && editingFund && editing.kind != TradeKinds.DIVIDEND) {
                toDecimalString(tradeGross(editing.units, editing.price, editing.gross))
            } else {
                ""
            },
        )
    }
    // A fund's fees, typed; a stock's are worked out.
    var typedFees by remember { mutableStateOf(if (editing != null && editingFund && editing.fees > 0) toDecimalString(editing.fees) else "") }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    val opened = remember { listOf(kind, symbol, account, date, time, units, price, amount, typedFees) }

    val fund = symbol?.let { isFund(it) } ?: false
    // A fund's subscription or redemption is entered as an amount, which the units follow.
    val byAmount = fund && kind != TradeKinds.DIVIDEND
    val security = symbol?.let { state.securitiesBySymbol[it] }
    val held = state.holdings.firstOrNull { it.symbol == symbol && it.position.accountId == account }
    // What can be sold now: what is held, plus what the sell being edited already takes out of it.
    val available = (held?.units ?: 0) +
        if (editing != null && editing.kind == TradeKinds.SELL && editing.symbol == symbol && editing.accountId == account) editing.units else 0
    // A dividend is paid on what was held, so its units are filled in from the history whenever the security, the
    // account or the date changes — and left as they are otherwise, since a sell after the ex-date still earns
    // the dividend on the shares it sold. An edit opens on the units it was saved with.
    val heldBefore = if (symbol != null && account != null) unitsHeldBefore(state.trades, account!!, symbol!!, date.toString()) else 0
    val fillKey = listOf(kind, symbol, account, date)
    var filledFor by remember { mutableStateOf(if (editing != null) fillKey else null) }
    LaunchedEffect(fillKey) {
        if (kind == TradeKinds.DIVIDEND && filledFor != fillKey) units = if (heldBefore > 0) unitsInput(heldBefore) else ""
        filledFor = fillKey
    }

    val unitsValue = parseUnits(units, whole = !fund)
    val priceValue = parsePrice(price)
    val amountValue = parseMoney(amount)?.takeIf { it > 0 }
    val feesValue = if (typedFees.isBlank()) 0L else parseMoney(typedFees)?.takeIf { it >= 0 }

    // A fund's units follow its amount and NAVPU, as the trust bank works them out, whenever either changes; they
    // are left editable for a confirmation that rounded them otherwise. An edit opens on the units it was saved with.
    val unitsKey = listOf(byAmount, symbol, amount, price)
    var unitsFilledFor by remember { mutableStateOf(if (editing != null) unitsKey else null) }
    LaunchedEffect(unitsKey) {
        if (byAmount && unitsFilledFor != unitsKey && amountValue != null && priceValue != null) {
            units = unitsInput(unitsFor(amountValue, priceValue))
        }
        unitsFilledFor = unitsKey
    }

    // What a dividend can be paid on: each security in each account it was ever traded in, plus the one being
    // edited. Picking one picks its account too, so the account is named beside a security only where the same one
    // is in two accounts and the name is what tells them apart.
    val dividendSources = remember(state.trades, state.securities, state.accounts, editing) {
        val names = state.accounts.associate { it.id to it.name }
        val bySymbol = state.securities.associateBy { it.symbol }
        val pairs = (state.trades.map { it.accountId to it.symbol } + listOfNotNull(editing?.let { it.accountId to it.symbol }))
            .distinct()
            .filter { (accountId, symbol) -> accountId in names && symbol in bySymbol }
        val shared = pairs.groupBy { it.second }.filterValues { it.size > 1 }.keys
        pairs.sortedWith(compareBy({ it.second }, { names[it.first] })).map { (accountId, symbol) ->
            val name = bySymbol.getValue(symbol).name
            val head = if (isFund(symbol)) name else "$symbol · $name"
            SelectOption("$accountId|$symbol", head + if (symbol in shared) " · ${names[accountId]}" else "")
        }
    }
    val dividendSource = "$account|$symbol"

    val occurredOn = date.format(DateTimeFormatter.ISO_LOCAL_DATE) + (time?.let { "T" + it.format(TIME_FORMAT) } ?: "")
    // A back-dated sell, or a buy edited down or moved after a sell that needs it, would sell more than is held.
    val historyHolds = unitsValue == null || priceValue == null || symbol == null || account == null ||
        positions(
            state.trades.filter { it.id != editing?.id } + Trade(
                id = editing?.id ?: "",
                accountId = account!!,
                symbol = symbol!!,
                kind = kind,
                occurredOn = occurredOn,
                units = unitsValue,
                price = priceValue,
                gross = if (byAmount) amountValue else null,
                fees = 0,
                amount = 0,
                transactionId = "",
                createdAt = editing?.createdAt ?: Instant.now().toString(),
            ),
        ) != null

    // The cash before fees: a fund's amount, or the units at the price.
    val gross = if (byAmount) amountValue else if (unitsValue != null && priceValue != null) valueOf(unitsValue, priceValue) else null
    val bank = state.accounts.firstOrNull { it.id == account }?.bank
    val minCommission = Banks.minCommission(bank)
    val roundsUp = Banks.roundsFeesUp(bank)
    // What comes off the gross: a fund's typed fees, a stock dividend's final tax, or a stock trade's PSE fees.
    val fees = when {
        fund -> feesValue ?: 0
        gross == null -> 0
        kind == TradeKinds.DIVIDEND -> dividendTax(gross)
        else -> pseFees(gross, kind == TradeKinds.SELL, minCommission, roundsUp).total
    }

    val form = rememberFormValidation()
    val stockField = form.field(
        "stock",
        if (security == null || kind == TradeKinds.DIVIDEND && dividendSources.none { it.value == dividendSource }) {
            stringResource(R.string.error_choose_security)
        } else {
            null
        },
    )
    val accountField = form.field(
        "account",
        if (state.accounts.none { it.id == account }) stringResource(if (fund) R.string.error_choose_account else R.string.error_choose_broker) else null,
    )
    val amountField = form.field("amount", if (byAmount && amountValue == null) stringResource(R.string.error_amount_number) else null)
    val unitsField = form.field(
        "units",
        when {
            unitsValue == null -> stringResource(if (fund) R.string.error_units_number else R.string.error_shares_whole)
            kind == TradeKinds.SELL && unitsValue > available -> stringResource(R.string.error_shares_over_held, unitsText(available))
            !historyHolds -> stringResource(if (fund) R.string.error_units_over_history else R.string.error_shares_over_history)
            else -> null
        },
    )
    val priceField = form.field("price", if (priceValue == null) stringResource(R.string.error_price_number) else null)
    val feesField = form.field(
        "fees",
        when {
            !fund -> null
            feesValue == null -> stringResource(R.string.error_fees_number)
            gross != null && kind != TradeKinds.BUY && gross - feesValue <= 0 -> stringResource(R.string.error_fees_too_high)
            else -> null
        },
    )

    FullScreenDialog(
        title = stringResource(if (editing != null) R.string.investments_edit_trade else R.string.investments_new_trade),
        onDismiss = onDismiss,
        onSave = submit@{
            val chosenSymbol = symbol ?: return@submit
            val chosenAccount = account ?: return@submit
            val count = unitsValue ?: return@submit
            val each = priceValue ?: return@submit
            busy.run("trade", snackbarHostState, successMessage = savedMessage, onSuccess = onDismiss) {
                onSave(TradeSubmission(chosenAccount, chosenSymbol, kind, occurredOn, count, each, if (byAmount) amountValue else null, fees))
            }
        },
        saveEnabled = form.valid(stockField, accountField, amountField, unitsField, priceField, feesField),
        submitting = busy.isBusy("trade"),
        dirty = listOf(kind, symbol, account, date, time, units, price, amount, typedFees) != opened,
    ) {
        ConnectedButtonGroup(
            options = KINDS,
            selected = kind,
            onSelect = { kind = it },
            label = { tradeKindLabel(it, symbol.orEmpty()) },
        )

        if (kind == TradeKinds.DIVIDEND) {
            // Only a security already traded can pay a dividend, so it is picked from those rather than searched
            // for, and the account it is in comes with it.
            DropdownField(
                label = stringResource(R.string.investments_label_security),
                value = dividendSources.firstOrNull { it.value == dividendSource }?.label ?: "",
                options = dividendSources,
                onSelect = { picked ->
                    account = picked.substringBefore('|')
                    symbol = picked.substringAfter('|')
                },
                field = stockField,
            )
        } else {
            SecurityField(
                securities = state.securities,
                selected = security,
                field = stockField,
                onSelect = { picked ->
                    symbol = picked.symbol
                    // The security fills in the account that already holds it, as a payee fills in the rest.
                    state.held.firstOrNull { it.symbol == picked.symbol }?.let { account = it.position.accountId }
                    price = priceInput(picked.price)
                },
            )
        }

        if (kind != TradeKinds.DIVIDEND) DropdownField(
            label = stringResource(if (fund) R.string.investments_label_fund_account else R.string.investments_label_broker),
            value = state.accounts.firstOrNull { it.id == account }?.name ?: "",
            options = state.accounts.map { SelectOption(it.id, it.name) },
            onSelect = { account = it },
            field = accountField,
            hint = held?.takeIf { it.units > 0 }?.let { stringResource(R.string.investments_held_hint, unitsText(it.units)) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PickerField(
                value = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                label = stringResource(R.string.label_date),
                onClick = { pickingDate = true },
                modifier = Modifier.weight(1f),
            )
            PickerField(
                value = time?.format(TIME_FORMAT) ?: "",
                label = stringResource(R.string.label_time),
                placeholder = stringResource(R.string.placeholder_optional),
                onClick = { pickingTime = true },
                modifier = Modifier.weight(1f),
            )
        }

        val priceInputField = @Composable {
            YuukaTextField(
                value = price,
                onValueChange = { price = it },
                label = stringResource(
                    when {
                        kind == TradeKinds.DIVIDEND && fund -> R.string.investments_label_distribution_per_unit
                        kind == TradeKinds.DIVIDEND -> R.string.investments_label_dividend_per_share
                        fund -> R.string.investments_label_navpu
                        else -> R.string.investments_label_price
                    },
                ),
                singleLine = true,
                field = priceField,
                prefix = currencySymbol("PHP"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }

        // A fund is bought for an amount and a NAVPU, which the units follow; a stock for a number of shares at a price.
        if (byAmount) {
            YuukaTextField(
                value = amount,
                onValueChange = { amount = it },
                label = stringResource(R.string.investments_label_amount),
                singleLine = true,
                field = amountField,
                prefix = currencySymbol("PHP"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        if (fund) priceInputField()

        val lot = if (fund) null else priceValue?.let { boardLot(it) }
        YuukaTextField(
            value = units,
            onValueChange = { units = it },
            label = stringResource(if (fund) R.string.investments_units else R.string.investments_shares),
            singleLine = true,
            field = unitsField,
            keyboardOptions = KeyboardOptions(keyboardType = if (fund) KeyboardType.Decimal else KeyboardType.Number),
            // A dividend is paid on whatever was held, so the board lot means nothing there.
            hint = when {
                byAmount -> stringResource(R.string.investments_units_hint)
                kind == TradeKinds.DIVIDEND || lot == null -> null
                unitsValue != null && (unitsValue / UNIT) % lot != 0L -> stringResource(R.string.investments_odd_lot_hint, "%,d".format(lot))
                else -> stringResource(R.string.investments_board_lot_hint, "%,d".format(lot))
            },
        )

        if (!fund) priceInputField()

        if (fund) {
            YuukaTextField(
                value = typedFees,
                onValueChange = { typedFees = it },
                label = stringResource(R.string.investments_label_fees),
                singleLine = true,
                field = feesField,
                prefix = currencySymbol("PHP"),
                placeholder = stringResource(R.string.placeholder_optional),
                hint = stringResource(R.string.investments_fees_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }

        if (gross != null && unitsValue != null) Breakdown(kind, fund, gross, fees, held, unitsValue, minCommission, roundsUp)
    }

    if (pickingDate) {
        YuukaDatePickerDialog(initial = date, onDismiss = { pickingDate = false }, onPicked = { date = it; pickingDate = false })
    }
    if (pickingTime) {
        YuukaTimePickerDialog(initial = time ?: LocalTime.now(), onDismiss = { pickingTime = false }, onPicked = { time = it; pickingTime = false })
    }
}

/** How a security reads in the picker: a stock's ticker and company, a fund's name and the trust entity that runs it. */
private fun pickerLabel(security: Security): String =
    if (security.kind == SecurityKinds.FUND) listOfNotNull(security.name, security.provider).joinToString(" · ") else "${security.symbol} · ${security.name}"

/**
 * The stock or fund, typed rather than scrolled to: the PSE lists hundreds, and the trust banks hundreds more UITFs.
 * What is typed is matched against symbols first, then names (and a fund's trust entity), and the menu offers the
 * closest few.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurityField(securities: List<Security>, selected: Security?, field: FieldState, onSelect: (Security) -> Unit) {
    var query by remember(selected?.symbol) { mutableStateOf(selected?.let { pickerLabel(it) } ?: "") }
    var open by remember { mutableStateOf(false) }
    val needle = query.trim().uppercase()
    val matches = remember(needle, securities) {
        if (needle.isEmpty() || selected != null && query == pickerLabel(selected)) {
            emptyList()
        } else {
            (
                securities.filter { it.symbol.startsWith(needle) } +
                    securities.filter { it.name.uppercase().contains(needle) || it.provider?.uppercase()?.contains(needle) == true }
                )
                .distinct()
                .take(8)
        }
    }

    ExposedDropdownMenuBox(expanded = open && matches.isNotEmpty(), onExpandedChange = { open = it }, modifier = Modifier.fillMaxWidth()) {
        YuukaTextField(
            value = query,
            onValueChange = {
                query = it
                open = true
            },
            label = stringResource(R.string.investments_label_security),
            singleLine = true,
            field = field,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = open && matches.isNotEmpty(), onDismissRequest = { open = false }) {
            matches.forEach { security ->
                DropdownMenuItem(
                    text = { Text(pickerLabel(security), maxLines = 1) },
                    onClick = {
                        onSelect(security)
                        query = pickerLabel(security)
                        open = false
                    },
                )
            }
        }
    }
}

/** What the trade comes to, fee by fee, ending on the figure that moves the account's cash. */
@Composable
private fun Breakdown(kind: String, fund: Boolean, gross: Long, fees: Long, held: Holding?, units: Long, minCommission: Long, roundsUp: Boolean) {
    val sell = kind == TradeKinds.SELL
    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FigureRow(stringResource(R.string.investments_gross)) { MoneyText(gross, style = MaterialTheme.typography.bodyLarge) }
            when {
                fund -> FigureRow(stringResource(R.string.investments_label_fees)) {
                    MoneyText(if (kind == TradeKinds.BUY) fees else -fees, style = MaterialTheme.typography.bodyLarge)
                }
                kind == TradeKinds.DIVIDEND -> FigureRow(stringResource(R.string.investments_final_tax)) {
                    MoneyText(-fees, style = MaterialTheme.typography.bodyLarge)
                }
                else -> {
                    val lines = pseFees(gross, sell, minCommission, roundsUp)
                    FigureRow(stringResource(R.string.investments_commission)) { MoneyText(lines.commission, style = MaterialTheme.typography.bodyLarge) }
                    FigureRow(stringResource(R.string.investments_vat)) { MoneyText(lines.vat, style = MaterialTheme.typography.bodyLarge) }
                    FigureRow(stringResource(R.string.investments_pse_fee)) { MoneyText(lines.pseFee, style = MaterialTheme.typography.bodyLarge) }
                    FigureRow(stringResource(R.string.investments_sccp)) { MoneyText(lines.sccp, style = MaterialTheme.typography.bodyLarge) }
                    if (sell) FigureRow(stringResource(R.string.investments_sales_tax)) { MoneyText(lines.salesTax, style = MaterialTheme.typography.bodyLarge) }
                }
            }
            HorizontalDivider()
            when (kind) {
                TradeKinds.BUY -> Total(stringResource(R.string.investments_total_cost), gross + fees)
                TradeKinds.SELL -> {
                    val proceeds = gross - fees
                    Total(stringResource(R.string.investments_net_proceeds), proceeds)
                    if (held != null && held.units > 0 && units <= held.units) {
                        val gain = proceeds - Math.round(held.costBasis * units.toDouble() / held.units)
                        FigureRow(stringResource(R.string.investments_gain_on_sale)) {
                            MoneyText(gain, tone = MoneyTone.SIGNED_ALERT, explicit = true, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                else -> Total(stringResource(if (fund) R.string.investments_net_distribution else R.string.investments_net_dividend), gross - fees)
            }
        }
    }
}

@Composable
private fun Total(label: String, amount: Long) {
    FigureRow(label) { MoneyText(amount, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)) }
}
