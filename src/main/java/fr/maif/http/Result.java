package fr.maif.http;

import java.util.Collection;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Result<R> {
    public final Optional<String> error;
    public final R value;

    public Result(String error) {
        this.error = Optional.ofNullable(error);
        this.value = null;
    }

    public Result(R value) {
        this.value = value;
        this.error = Optional.empty();
    }

    public boolean isError() {
        return error.isPresent();
    }

    public <O> Result<O> map(Function<R, O> mapper) {
        return error.map(e -> new Result<O>(e)).orElseGet(() -> new Result<>(mapper.apply(value)));
    }

    public static <I,O> Result<O> merge(
            Collection<Result<I>> results,
            BiFunction<O,I,O> mergeFunction,
            O base
    ) {
        var res = base;
        for(Result<I> next: results) {
            if(next.isError()) {
                return new Result<>(next.error.get());
            }
            res = mergeFunction.apply(res, next.value);
        }

        return new Result<>(res);
    }
}
